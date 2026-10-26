package com.petzo.service;

import com.petzo.common.ApiException;
import com.petzo.domain.Pet;
import com.petzo.domain.Profile;
import com.petzo.domain.Repos.*;
import com.petzo.domain.Simple.Report;
import com.petzo.domain.Types.*;
import com.petzo.storage.StorageClient;
import com.petzo.web.Dtos.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminService {

	private final PetRepo pets;
	private final ProfileRepo profiles;
	private final ReportRepo reports;
	private final PetService petService;
	private final StorageClient storage;
	private final Events events;
	private final JdbcTemplate jdbc;

	public AdminService(PetRepo pets, ProfileRepo profiles, ReportRepo reports, PetService petService, StorageClient storage, Events events, JdbcTemplate jdbc) {
		this.pets = pets;
		this.profiles = profiles;
		this.reports = reports;
		this.petService = petService;
		this.storage = storage;
		this.events = events;
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public List<PetDto> moderationQueue() {
		return pets.findByStatusOrderByCreatedAt(PetStatus.pending_review).stream().map(p -> petService.toDto(p, true, null)).toList();
	}

	/** version = the one shown in the queue; a pet edited since then must be reviewed again. */
	public PetDto approve(UUID id, Integer version) {
		Pet pet = pets.findById(id).orElseThrow(ApiException::notFound);
		if (pet.status != PetStatus.pending_review) throw ApiException.conflict("INVALID_STATUS", "Solo se aprueban publicaciones en revisión");
		if (version != null && version != pet.version) throw ApiException.conflict("STALE", "La publicación cambió; recarga la cola");
		pet.status = PetStatus.approved;
		pet.moderationReason = null;
		events.petStatusChanged(pets.saveAndFlush(pet));
		return petService.toDto(pet, true, null);
	}

	/** Rejected photos would stay public (bucket is public), so they are deleted (SPEC §15). */
	public PetDto reject(UUID id, String reason) {
		Pet pet = pets.findById(id).orElseThrow(ApiException::notFound);
		// paused too: otherwise a reported pet could be paused to dodge the takedown and then republished without moderation
		if (pet.status != PetStatus.pending_review && pet.status != PetStatus.approved && pet.status != PetStatus.paused) throw ApiException.conflict("INVALID_STATUS", "No se puede rechazar en este estado");
		List<String> paths = pet.photos.stream().map(ph -> ph.storagePath).toList();
		pet.status = PetStatus.rejected;
		pet.moderationReason = reason.trim();
		pet.photos.clear();
		pets.saveAndFlush(pet);
		storage.deleteAfterCommit(paths);
		events.petStatusChanged(pet);
		return petService.toDto(pet, true, null);
	}

	@Transactional(readOnly = true)
	public List<ProfileDto> foundations() {
		return profiles.findByVerificationStatusOrderByUpdatedAtDesc(VerificationStatus.pending).stream().map(p -> ProfileService.toDto(p, storage)).toList();
	}

	// Verified foundations get the Pro plan (SPEC §2)
	public ProfileDto verify(UUID userId, boolean approve) {
		Profile p = profiles.findById(userId).orElseThrow(ApiException::notFound);
		p.verificationStatus = approve ? VerificationStatus.verified : VerificationStatus.rejected;
		if (approve) p.plan = Plan.pro;
		return ProfileService.toDto(profiles.saveAndFlush(p), storage);
	}

	public ProfileDto setPlan(UUID userId, Plan plan) {
		Profile p = profiles.findById(userId).orElseThrow(ApiException::notFound);
		p.plan = plan;
		return ProfileService.toDto(profiles.saveAndFlush(p), storage);
	}

	public ProfileDto suspend(UUID userId, boolean suspended) {
		Profile p = profiles.findById(userId).orElseThrow(ApiException::notFound);
		// One misclick on your own row would lock you out of the panel
		if (suspended && p.role == com.petzo.domain.Types.Role.admin) throw ApiException.conflict("ADMIN_PROTECTED", "No se puede suspender a un administrador");
		p.suspended = suspended;
		return ProfileService.toDto(profiles.saveAndFlush(p), storage);
	}

	@Transactional(readOnly = true)
	public List<ProfileDto> users() {
		return profiles.findAll(org.springframework.data.domain.PageRequest.of(0, 200, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")))
				.map(p -> ProfileService.toDto(p, storage)).getContent();
	}

	@Transactional(readOnly = true)
	public List<ReportDto> openReports() {
		return reports.findByStatusOrderByCreatedAt(ReportStatus.open).stream()
				.map(r -> new ReportDto(r.id, r.petId, pets.findById(r.petId).map(p -> p.name).orElse("—"), r.reason, r.createdAt)).toList();
	}

	public void resolveReport(UUID id) {
		Report r = reports.findById(id).orElseThrow(ApiException::notFound);
		r.status = ReportStatus.resolved;
		reports.save(r);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> stats() {
		return jdbc.queryForMap("""
				select (select count(*) from pets where status in ('pending_review','approved')) as active_pets,
				       (select count(*) from pets where status = 'pending_review') as pending_review,
				       (select count(*) from pets where status = 'rejected') as rejected,
				       (select count(*) from pets where status = 'adopted') as adopted,
				       (select count(*) from adoption_requests) as requests,
				       (select count(*) from profiles) as users
				""");
	}
}
