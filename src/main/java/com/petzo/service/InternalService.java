package com.petzo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petzo.ai.AiService;
import com.petzo.common.ApiException;
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
	private final HttpClient download = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

	public InternalService(PetRepo pets, ProfileRepo profiles, ImportJobRepo jobs, StorageClient storage, AiService ai, Events events, JdbcTemplate jdbc, ObjectMapper json) {
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
	 * Ignored if the pet changed after n8n read it (stale result) or left review.
	 */
	public String moderationResult(UUID petId, ModerationResult in) {
		ai.record("moderation", "pet", petId, in.inputTokens(), in.outputTokens(), json.valueToTree(in));
		Pet pet = pets.findById(petId).orElseThrow(ApiException::notFound);
		if (pet.status != PetStatus.pending_review || (in.updatedAt() != null && !in.updatedAt().equals(String.valueOf(pet.updatedAt)))) return "ignored";
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

	/** Atomic claim: two workers never get the same job. */
	public Optional<Map<String, Object>> claimNext() {
		return jdbc.query("""
				update import_jobs set status = 'running' where id = (
				  select id from import_jobs where status = 'queued' order by created_at limit 1 for update skip locked)
				returning id, instagram_handle""",
				rs -> rs.next() ? Optional.of(Map.<String, Object>of("id", rs.getObject(1), "instagramHandle", rs.getString(2))) : Optional.empty());
	}

	/** Drafts belong to the foundation with that Instagram handle, else to the admin who launched the import. */
	public int addImportedPets(UUID jobId, ImportedPets in) {
		ImportJob job = jobs.findById(jobId).orElseThrow(ApiException::notFound);
		if (job.status != ImportStatus.running) throw ApiException.conflict("INVALID_STATUS", "El trabajo no está en curso");
		Profile owner = profiles.findAll().stream().filter(p -> job.instagramHandle.equalsIgnoreCase(p.foundationInstagram)).findFirst()
				.orElseGet(() -> profiles.findById(job.requestedBy).orElseThrow());
		int created = 0;
		for (ImportedPet ip : in.pets()) {
			Pet pet = new Pet();
			pet.owner = owner;
			pet.species = ip.species() == null ? Species.dog : ip.species();
			pet.name = ip.name() == null || ip.name().isBlank() ? "Sin nombre" : ip.name().trim();
			pet.ageMonths = ip.ageMonths();
			pet.sex = ip.sex() == null ? Sex.female : ip.sex();
			pet.size = ip.size() == null ? Size.medium : ip.size();
			pet.municipalityId = owner.municipalityId != null ? owner.municipalityId : 1;
			pet.description = ip.description() == null ? null : ip.description().substring(0, Math.min(1000, ip.description().length()));
			pet.status = PetStatus.draft;
			pet.source = PetSource.instagram;
			pet.sourceUrl = ip.sourceUrl();
			pets.saveAndFlush(pet);
			for (String url : ip.photoUrls().stream().limit(5).toList()) {
				fetchImage(url).ifPresent(bytes -> {
					String path = "pets/" + pet.id + "/" + UUID.randomUUID() + ".jpg";
					storage.upload(path, bytes, "image/jpeg");
					PetPhoto ph = new PetPhoto();
					ph.pet = pet;
					ph.storagePath = path;
					ph.position = (short) pet.photos.size();
					pet.photos.add(ph);
				});
			}
			pets.save(pet);
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

	// SSRF guard: only https images from Instagram's CDNs, max 5 MB
	private Optional<byte[]> fetchImage(String url) {
		try {
			URI uri = URI.create(url);
			String host = String.valueOf(uri.getHost());
			if (!"https".equals(uri.getScheme()) || !(host.endsWith(".cdninstagram.com") || host.endsWith(".fbcdn.net"))) return Optional.empty();
			HttpResponse<byte[]> res = download.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofByteArray());
			if (res.statusCode() != 200 || res.body().length > 5_000_000) return Optional.empty();
			return Optional.of(res.body());
		} catch (Exception e) {
			log.warn("image download failed: {}", e.getMessage());
			return Optional.empty();
		}
	}

}
