package com.petzo.service;

import com.petzo.common.ApiException;
import com.petzo.domain.Pet;
import com.petzo.domain.PetPhoto;
import com.petzo.domain.Profile;
import com.petzo.domain.Repos.PetRepo;
import com.petzo.domain.Types.*;
import com.petzo.storage.StorageClient;
import com.petzo.web.Dtos.*;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PetService {

	public static final int PAGE_SIZE = 20;
	public static final Map<Plan, Integer> PLAN_LIMITS = Map.of(Plan.free, 3, Plan.pro, 30);
	private static final Set<PetStatus> ACTIVE = EnumSet.of(PetStatus.pending_review, PetStatus.approved);

	// Owner transitions (RF-09). Approve/reject belong to moderation (n8n) and admins.
	private static final Map<PetStatus, Set<PetStatus>> TRANSITIONS = Map.of(
			PetStatus.paused, EnumSet.of(PetStatus.approved),
			PetStatus.approved, EnumSet.of(PetStatus.paused), // republish: paused content is unchanged
			PetStatus.adopted, EnumSet.of(PetStatus.approved, PetStatus.paused, PetStatus.pending_review),
			PetStatus.pending_review, EnumSet.of(PetStatus.draft)); // imported drafts

	private final PetRepo pets;
	private final StorageClient storage;
	private final Events events;
	private final jakarta.persistence.EntityManager em;

	public PetService(PetRepo pets, StorageClient storage, Events events, jakarta.persistence.EntityManager em) {
		this.em = em;
		this.pets = pets;
		this.storage = storage;
		this.events = events;
	}

	// ---- reads ----

	@Transactional(readOnly = true)
	public Page<PetDto> catalog(Species species, List<Size> sizes, Sex sex, Short ageMin, Short ageMax, Short municipalityId, int page) {
		Specification<Pet> spec = (root, q, cb) -> {
			List<Predicate> p = new ArrayList<>();
			p.add(cb.equal(root.get("status"), PetStatus.approved));
			p.add(cb.isFalse(root.get("owner").get("suspended")));
			if (species != null) p.add(cb.equal(root.get("species"), species));
			if (sizes != null && !sizes.isEmpty()) p.add(root.get("size").in(sizes));
			if (sex != null) p.add(cb.equal(root.get("sex"), sex));
			if (ageMin != null) p.add(cb.ge(root.get("ageMonths"), ageMin));
			if (ageMax != null) p.add(cb.le(root.get("ageMonths"), ageMax));
			if (municipalityId != null) p.add(cb.equal(root.get("municipalityId"), municipalityId));
			return cb.and(p.toArray(Predicate[]::new));
		};
		var result = pets.findAll(spec, PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
		return new Page<>(result.map(p -> toDto(p, false, null)).getContent(), page, PAGE_SIZE, result.getTotalElements());
	}

	@Transactional(readOnly = true)
	public PetDto detail(UUID id, UUID viewerId) {
		Pet pet = pets.findById(id).orElseThrow(ApiException::notFound);
		boolean own = pet.owner.id.equals(viewerId);
		if (!own && (pet.status != PetStatus.approved || pet.owner.suspended)) throw ApiException.notFound();
		return toDto(pet, own, pets.countByOwnerIdAndStatus(pet.owner.id, PetStatus.approved));
	}

	@Transactional(readOnly = true)
	public List<PetDto> mine(Profile me) {
		return pets.findByOwnerIdOrderByCreatedAtDesc(me.id).stream().map(p -> toDto(p, true, null)).toList();
	}

	// ---- writes ----

	public PetDto create(Profile me, PetInput in) {
		CurrentUser.requireComplete(me);
		assertPlanLimit(me, null);
		Pet pet = new Pet();
		pet.owner = me;
		apply(pet, in);
		// Draft until the owner attaches photos and submits (PATCH status pending_review), so moderation runs once
		// and a pet whose uploads failed can still be deleted instead of holding a plan slot
		pet.status = PetStatus.draft;
		return toDto(pets.saveAndFlush(pet), true, null);
	}

	public PetDto update(Profile me, UUID id, PetInput in) {
		Pet pet = own(me, id);
		if (pet.status == PetStatus.adopted) throw ApiException.conflict("INVALID_STATUS", "La mascota ya fue adoptada");
		// Any edit is moderated again; paused/rejected ones re-enter the plan count
		if (!pet.isActive()) assertPlanLimit(me, pet.id);
		apply(pet, in);
		resubmit(pet);
		return toDto(pets.saveAndFlush(pet), true, null);
	}

	public PetDto changeStatus(Profile me, UUID id, PetStatus to) {
		Pet pet = own(me, id);
		if (!TRANSITIONS.getOrDefault(to, Set.of()).contains(pet.status)) {
			throw ApiException.conflict("INVALID_STATUS", "No se puede pasar de " + pet.status + " a " + to);
		}
		if (ACTIVE.contains(to)) assertPlanLimit(me, pet.id);
		if (to == PetStatus.pending_review && pet.photos.isEmpty()) throw ApiException.conflict("PHOTOS_REQUIRED", "Agrega entre 1 y 5 fotos");
		pet.status = to;
		pets.saveAndFlush(pet);
		if (to == PetStatus.pending_review) events.moderate(pet);
		events.petStatusChanged(pet);
		return toDto(pet, true, null);
	}

	/** Stores one photo and returns its path; it joins the gallery only with PUT /photos (D-28). */
	public PhotoPath uploadPhoto(Profile me, UUID id, org.springframework.web.multipart.MultipartFile file) {
		Pet pet = editable(me, id);
		// No count check here: a full gallery may upload a replacement before PUT /photos drops the old one (max 5 is enforced on save)
		byte[] bytes = StorageClient.readJpeg(file);
		String path = "pets/" + pet.id + "/" + UUID.randomUUID() + ".jpg";
		storage.upload(path, bytes, "image/jpeg");
		return new PhotoPath(path);
	}

	/** Only drafts (Instagram imports the owner does not want); published pets are paused or marked adopted instead. */
	public void deleteDraft(Profile me, UUID id) {
		Pet pet = own(me, id);
		if (pet.status != PetStatus.draft) throw ApiException.conflict("INVALID_STATUS", "Solo se pueden eliminar borradores");
		List<String> paths = pet.photos.stream().map(ph -> ph.storagePath).toList();
		pets.delete(pet);
		storage.deleteAfterCommit(paths);
	}

	/**
	 * Sets the whole gallery in one step (first = cover): kept paths stay, new ones must already be uploaded, missing ones are deleted.
	 * One version bump and at most one moderation run, whatever changed.
	 */
	public PetDto replacePhotos(Profile me, UUID id, List<String> paths) {
		Pet pet = editable(me, id);
		var invalid = new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PHOTOS", "Foto inválida");
		if (Set.copyOf(paths).size() != paths.size()) throw invalid;
		Set<String> current = pet.photos.stream().map(ph -> ph.storagePath).collect(java.util.stream.Collectors.toSet());
		List<String> added = paths.stream().filter(p -> !current.contains(p)).toList();
		for (String path : added) {
			if (!Pattern.matches("pets/" + pet.id + "/[0-9a-f-]{36}\\.jpg", path)) throw invalid;
			if (!storage.exists(path)) throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PHOTOS", "La foto no se subió");
		}
		List<String> removed = current.stream().filter(p -> !paths.contains(p)).toList();
		pet.photos.removeIf(ph -> removed.contains(ph.storagePath));
		for (String path : added) {
			PetPhoto photo = new PetPhoto();
			photo.pet = pet;
			photo.storagePath = path;
			pet.photos.add(photo);
		}
		for (PetPhoto ph : pet.photos) ph.position = (short) paths.indexOf(ph.storagePath);
		pet.photos.sort(java.util.Comparator.comparingInt(ph -> ph.position));
		// Always bumped: concurrent gallery edits conflict instead of mixing positions. The bump also discards any
		// in-flight moderation result, so a pet already in review is moderated again even if only the order changed.
		em.lock(pet, jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT);
		if (!added.isEmpty() || !removed.isEmpty() || pet.status == PetStatus.pending_review) resubmit(pet);
		pets.saveAndFlush(pet);
		storage.deleteAfterCommit(removed);
		return toDto(pet, true, null);
	}

	// ---- helpers ----

	private void resubmit(Pet pet) {
		if (pet.status == PetStatus.draft) return; // imports stay drafts until submitted
		// Rejected pets lose their photos: without photos they go back to draft, never to the admin queue
		pet.status = pet.photos.isEmpty() ? PetStatus.draft : PetStatus.pending_review;
		pet.moderationReason = null;
		pet.riskScore = null;
		if (!pet.photos.isEmpty()) events.moderate(pet);
	}

	private Pet own(Profile me, UUID id) {
		Pet pet = pets.findById(id).orElseThrow(ApiException::notFound);
		if (!pet.owner.id.equals(me.id)) throw ApiException.notFound();
		return pet;
	}

	private Pet editable(Profile me, UUID id) {
		Pet pet = own(me, id);
		if (pet.status == PetStatus.adopted) throw ApiException.conflict("INVALID_STATUS", "La mascota ya fue adoptada");
		if (!pet.isActive() && pet.status != PetStatus.draft) assertPlanLimit(me, pet.id);
		return pet;
	}

	// shortcut: count-then-write; two concurrent submits can pass the limit. Move to a DB constraint if abused.
	private void assertPlanLimit(Profile me, UUID exceptPetId) {
		long active = pets.countByOwnerIdAndStatusIn(me.id, ACTIVE);
		if (exceptPetId != null && pets.findById(exceptPetId).map(Pet::isActive).orElse(false)) active--;
		int limit = PLAN_LIMITS.get(me.plan);
		if (active >= limit) throw ApiException.conflict("PLAN_LIMIT", "Tu plan permite " + limit + " publicaciones activas");
	}

	private static void apply(Pet pet, PetInput in) {
		pet.species = in.species();
		pet.name = in.name().trim();
		pet.ageMonths = in.ageMonths();
		pet.sex = in.sex();
		pet.size = in.size();
		pet.municipalityId = in.municipalityId();
		pet.vaccinated = in.vaccinated();
		pet.sterilized = in.sterilized();
		pet.dewormed = in.dewormed();
		pet.description = in.description() == null ? null : in.description().trim();
	}

	public PetDto toDto(Pet p, boolean privileged, Long publisherCount) {
		Profile o = p.owner;
		String name = o.isVerifiedFoundation() || (o.accountType == AccountType.foundation && o.foundationName != null) ? o.foundationName : o.fullName;
		var publisher = new PublisherDto(o.id, name, storage.clientAvatar(o.avatarUrl), o.municipalityId, o.accountType == AccountType.foundation, o.isVerifiedFoundation(), publisherCount);
		var photos = p.photos.stream().map(ph -> new PhotoDto(ph.id, storage.clientUrl(ph.storagePath), ph.storagePath)).toList();
		return new PetDto(p.id, p.species, p.name, p.ageMonths, p.sex, p.size, p.municipalityId, p.vaccinated, p.sterilized, p.dewormed,
				p.description, p.status, p.source, p.version, photos, publisher, privileged ? p.moderationReason : null, privileged ? p.riskScore : null,
				p.createdAt, p.updatedAt);
	}
}
