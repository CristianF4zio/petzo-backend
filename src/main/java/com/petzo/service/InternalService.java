package com.petzo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petzo.ai.AiService;
import com.petzo.common.ApiException;
import com.petzo.config.PetzoProperties;
import com.petzo.domain.Pet;
import com.petzo.domain.PetPhoto;
import com.petzo.domain.Profile;
import com.petzo.domain.Repos.*;
import com.petzo.domain.Simple.ImportJob;
import com.petzo.domain.Types.*;
import com.petzo.storage.StorageClient;
import com.petzo.web.Dtos.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** /internal/** callbacks from n8n (moderation) and the Stagehand worker (imports). */
@Service
@Transactional
public class InternalService {

	private static final Logger log = LoggerFactory.getLogger(InternalService.class);
	private static final int RISK_THRESHOLD = 40;

	private final PetRepo pets;
	private final ProfileRepo profiles;
	private final ImportJobRepo jobs;
	private final StorageClient storage;
	private final AiService ai;
	private final Events events;
	private final JdbcTemplate jdbc;
	private final ObjectMapper json;
	private final PetzoProperties props;
	private final HttpClient download = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();

	public InternalService(PetRepo pets, ProfileRepo profiles, ImportJobRepo jobs, StorageClient storage, AiService ai, Events events, JdbcTemplate jdbc, ObjectMapper json, PetzoProperties props) {
		this.props = props;
		this.pets = pets;
		this.profiles = profiles;
		this.jobs = jobs;
		this.storage = storage;
		this.ai = ai;
		this.events = events;
		this.jdbc = jdbc;
		this.json = json;
	}

	/**
	 * Low risk → approved; otherwise it stays in the admin queue with the reason.
	 * Ignored if the pet changed after n8n read it (version) or left review; a concurrent edit fails with STALE.
	 */
	public String moderationResult(UUID petId, ModerationResult in) {
		ai.record("moderation", "pet", petId, in.inputTokens(), in.outputTokens(), json.valueToTree(in));
		Pet pet = pets.findById(petId).orElseThrow(ApiException::notFound);
		if (pet.status != PetStatus.pending_review || in.version() != pet.version) return "ignored";
		pet.riskScore = (short) Math.max(0, Math.min(100, in.riskScore()));
		pet.moderationReason = in.reason();
		if (pet.riskScore < RISK_THRESHOLD) {
			pet.status = PetStatus.approved;
			pet.moderationReason = null;
		}
		pets.saveAndFlush(pet);
		if (pet.status == PetStatus.approved) events.petStatusChanged(pet);
		return pet.status.name();
	}

	// ---- imports (Stagehand worker) ----

	public ImportJob createImport(Profile admin, String handle) {
		ImportJob job = new ImportJob();
		job.instagramHandle = handle.replaceFirst("^@", "");
		job.requestedBy = admin.id;
		return jobs.save(job);
	}

	@Transactional(readOnly = true)
	public List<ImportJob> imports() { return jobs.findTop50ByOrderByCreatedAtDesc(); }

	/** Foundations read by Stagehand: latest name and page, how many runs and pets, and whether a verified account owns the handle. */
	@Transactional(readOnly = true)
	public List<ImportedFoundation> importedFoundations() {
		return jdbc.query("""
				select j.instagram_handle,
				  (select x.foundation_name from import_jobs x where x.instagram_handle = j.instagram_handle and x.foundation_name is not null order by x.created_at desc limit 1),
				  (select x.source_url from import_jobs x where x.instagram_handle = j.instagram_handle and x.source_url is not null order by x.created_at desc limit 1),
				  count(*), max(j.created_at),
				  (select x.status::text from import_jobs x where x.instagram_handle = j.instagram_handle order by x.created_at desc limit 1),
				  coalesce(sum(j.pets_created), 0),
				  exists (select 1 from profiles p where lower(p.foundation_instagram) = lower(j.instagram_handle) and p.verification_status = 'verified')
				from import_jobs j group by j.instagram_handle order by max(j.created_at) desc limit 100""",
				(rs, i) -> new ImportedFoundation(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getTimestamp(5).toInstant(), rs.getString(6), rs.getLong(7), rs.getBoolean(8)));
	}

	/**
	 * Queues one import per configured foundation page on a schedule (IMPORT_CRON, Caracas time); the worker picks it up.
	 * Skips a page that already has a job queued or running, so a slow run is never doubled.
	 */
	@org.springframework.scheduling.annotation.Scheduled(cron = "${petzo.import-cron}", zone = "America/Caracas")
	public int scheduleImports() {
		Profile admin = profiles.findFirstByRole(Role.admin).orElse(null);
		if (admin == null) {
			log.warn("scheduled import skipped: no admin to own the drafts");
			return 0;
		}
		// A worker that died mid-run (or lost the API) leaves its job "running" forever, which would block every later run
		int stale = jdbc.update("update import_jobs set status = 'failed', error = 'Sin respuesta del worker' where status = 'running' and created_at < now() - interval '15 minutes'");
		if (stale > 0) log.warn("stale import jobs marked failed: {}", stale);
		int queued = 0;
		for (String handle : props.importAutoHandles() == null ? List.<String>of() : props.importAutoHandles()) {
			if (handle.isBlank() || jobs.existsByInstagramHandleAndStatusIn(handle, List.of(ImportStatus.queued, ImportStatus.running))) continue;
			createImport(admin, handle);
			queued++;
		}
		if (queued > 0) log.info("scheduled imports queued: {}", queued);
		return queued;
	}

	/** Atomic claim: two workers never get the same job. */
	public Optional<Map<String, Object>> claimNext() {
		return jdbc.query("""
				update import_jobs set status = 'running' where id = (
				  select id from import_jobs where status = 'queued' order by created_at limit 1 for update skip locked)
				returning id, instagram_handle""",
				rs -> rs.next() ? Optional.of(Map.<String, Object>of("id", rs.getObject(1), "instagramHandle", rs.getString(2))) : Optional.empty());
	}

	/**
	 * Imported pets belong to the verified foundation with that handle, else to the admin who launched the import.
	 * With a photo: a verified foundation's pets are published right away (the admin already vetted the foundation, so
	 * they show in Explorar); any other source goes to moderation (n8n + admin). Without a photo they stay drafts.
	 * Not transactional: image downloads (slow) must not hold a DB connection; each save commits on its own.
	 */
	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
	public int addImportedPets(UUID jobId, ImportedPets in) {
		ImportJob job = jobs.findById(jobId).orElseThrow(ApiException::notFound);
		if (job.status != ImportStatus.running) throw ApiException.conflict("INVALID_STATUS", "El trabajo no está en curso");
		Optional<Profile> foundation = profiles.findFirstByFoundationInstagramIgnoreCaseAndVerificationStatus(job.instagramHandle, VerificationStatus.verified);
		Profile owner = foundation.orElseGet(() -> profiles.findById(job.requestedBy).orElseThrow());
		boolean trusted = foundation.isPresent();
		if (in.foundationName() != null && !in.foundationName().isBlank()) job.foundationName = cut(in.foundationName().trim(), 120);
		if (in.sourceUrl() != null && !in.sourceUrl().isBlank()) job.sourceUrl = cut(in.sourceUrl(), 500);
		int created = 0;
		for (ImportedPet ip : in.pets()) {
			String name = ip.name() == null || ip.name().isBlank() ? "Sin nombre" : cut(ip.name().trim(), 60);
			String sourceUrl = ip.sourceUrl() == null ? null : cut(ip.sourceUrl(), 500);
			// Scheduled imports read the same page again and again: a pet already imported from it is not created twice
			if (sourceUrl != null && pets.existsByOwnerIdAndSourceUrlAndNameIgnoreCase(owner.id, sourceUrl, name)) continue;
			Pet pet = new Pet();
			pet.owner = owner;
			pet.species = ip.species() == null ? Species.dog : ip.species();
			pet.name = name;
			pet.ageMonths = ip.ageMonths() == null ? null : (short) Math.clamp(ip.ageMonths(), 0, 360);
			pet.sex = ip.sex() == null ? Sex.female : ip.sex();
			pet.size = ip.size() == null ? Size.medium : ip.size();
			pet.municipalityId = owner.municipalityId != null ? owner.municipalityId : 1;
			pet.description = ip.description() == null ? null : cut(ip.description(), 1000);
			pet.status = PetStatus.draft;
			pet.source = PetSource.instagram;
			pet.sourceUrl = sourceUrl;
			Pet saved = pets.save(pet);
			for (String url : ip.photoUrls().stream().limit(5).toList()) {
				fetchImage(url).ifPresent(bytes -> {
					String path = "pets/" + saved.id + "/" + UUID.randomUUID() + ".jpg";
					storage.upload(path, bytes, "image/jpeg");
					PetPhoto ph = new PetPhoto();
					ph.pet = saved;
					ph.storagePath = path;
					ph.position = (short) saved.photos.size();
					saved.photos.add(ph);
				});
			}
			if (!saved.photos.isEmpty()) {
				saved.status = trusted ? PetStatus.approved : PetStatus.pending_review;
				Pet stored = pets.saveAndFlush(saved);
				if (trusted) events.petStatusChanged(stored); // "ya está publicado" email to the foundation
				else events.moderate(stored);
			}
			created++;
		}
		job.petsCreated += created;
		jobs.save(job);
		return created;
	}

	public void finishImport(UUID jobId, ImportStatusInput in) {
		ImportJob job = jobs.findById(jobId).orElseThrow(ApiException::notFound);
		job.status = in.status();
		job.error = in.error();
		jobs.save(job);
		ai.record("import", "import_job", jobId, in.inputTokens(), in.outputTokens(), json.valueToTree(Map.of("status", in.status(), "pets", job.petsCreated)));
	}

	// SSRF guard: only images from IMPORT_ALLOWED_HOSTS (no redirects elsewhere), read at most 5 MB
	private static String cut(String s, int max) { return s.substring(0, Math.min(max, s.length())); }

	private Optional<byte[]> fetchImage(String url) {
		try {
			URI uri = URI.create(url);
			String host = String.valueOf(uri.getHost());
			// SSRF guard: only the configured demo foundation site (IMPORT_ALLOWED_HOSTS), never arbitrary hosts
			if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || !props.importAllowedHosts().contains(host)) return Optional.empty();
			HttpResponse<java.io.InputStream> res = download.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofInputStream());
			try (var body = res.body()) {
				if (res.statusCode() != 200) return Optional.empty();
				byte[] bytes = body.readNBytes(5_000_001);
				return bytes.length > 5_000_000 ? Optional.empty() : Optional.of(bytes);
			}
		} catch (Exception e) {
			log.warn("image download failed: {}", e.getMessage());
			return Optional.empty();
		}
	}

}
