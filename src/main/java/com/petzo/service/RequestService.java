package com.petzo.service;

import com.petzo.common.ApiException;
import com.petzo.domain.AdoptionRequest;
import com.petzo.domain.Pet;
import com.petzo.domain.Profile;
import com.petzo.domain.Repos.PetRepo;
import com.petzo.domain.Repos.RequestRepo;
import com.petzo.domain.Types.PetStatus;
import com.petzo.domain.Types.RequestStatus;
import com.petzo.storage.StorageClient;
import com.petzo.web.Dtos.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional
public class RequestService {

	private final RequestRepo requests;
	private final PetRepo pets;
	private final PreEvaluator preEvaluator;
	private final Events events;
	private final StorageClient storage;

	public RequestService(RequestRepo requests, PetRepo pets, PreEvaluator preEvaluator, Events events, StorageClient storage) {
		this.requests = requests;
		this.pets = pets;
		this.preEvaluator = preEvaluator;
		this.events = events;
		this.storage = storage;
	}

	public RequestDto create(Profile me, UUID petId, RequestInput in) {
		CurrentUser.requireComplete(me);
		Pet pet = pets.findVisible(List.of(petId)).stream().findFirst().orElseThrow(ApiException::notFound);
		if (pet.owner.id.equals(me.id)) throw ApiException.conflict("OWN_PET", "No puedes adoptar tu propia publicación");
		if (requests.existsByPetIdAndAdopterIdAndStatus(petId, me.id, RequestStatus.pending)) {
			throw ApiException.conflict("ALREADY_REQUESTED", "Ya tienes una solicitud activa para esta mascota");
		}
		AdoptionRequest r = new AdoptionRequest();
		r.pet = pet;
		r.adopter = me;
		r.answers = in.answers();
		r.motivation = in.motivation().trim();
		requests.saveAndFlush(r);
		UUID id = r.id;
		// AI runs after commit so a slow or failed call never loses the request (HU-08)
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override public void afterCommit() { preEvaluator.run(id); }
		});
		events.requestCreated(r);
		return toDto(r);
	}

	@Transactional(readOnly = true)
	public List<RequestDto> forPet(Profile me, UUID petId) {
		Pet pet = pets.findById(petId).orElseThrow(ApiException::notFound);
		if (!pet.owner.id.equals(me.id)) throw ApiException.notFound();
		return requests.findByPetIdOrderByCreatedAtDesc(petId).stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public List<MyRequestDto> mine(Profile me) {
		return requests.findByAdopterIdOrderByCreatedAtDesc(me.id).stream().map(r -> new MyRequestDto(r.id, r.status, r.pet.id, r.pet.name,
				r.pet.photos.isEmpty() ? null : storage.publicUrl(r.pet.photos.get(0).storagePath), r.createdAt, r.decidedAt)).toList();
	}

	// HU-09: approving one request leaves the others pending
	public RequestDto decide(Profile me, UUID requestId, boolean approve) {
		AdoptionRequest r = requests.findById(requestId).orElseThrow(ApiException::notFound);
		if (!r.pet.owner.id.equals(me.id)) throw ApiException.notFound();
		if (r.status != RequestStatus.pending) throw ApiException.conflict("INVALID_STATUS", "La solicitud ya fue respondida");
		if (approve && r.pet.status == PetStatus.adopted) throw ApiException.conflict("INVALID_STATUS", "La mascota ya fue adoptada");
		r.status = approve ? RequestStatus.approved : RequestStatus.rejected;
		r.decidedAt = Instant.now();
		requests.saveAndFlush(r);
		events.requestStatusChanged(r);
		return toDto(r);
	}

	/** RNF-04: WhatsApp only flows between the two sides of an approved request. */
	@Transactional(readOnly = true)
	public ContactDto contact(Profile me, UUID requestId) {
		AdoptionRequest r = requests.findById(requestId).orElseThrow(ApiException::notFound);
		boolean owner = r.pet.owner.id.equals(me.id), adopter = r.adopter.id.equals(me.id);
		if (!owner && !adopter) throw ApiException.notFound();
		if (r.status != RequestStatus.approved) throw ApiException.conflict("NOT_APPROVED", "El contacto se libera cuando se aprueba la solicitud");
		Profile other = owner ? r.adopter : r.pet.owner;
		return new ContactDto(other.fullName, other.whatsapp);
	}

	private RequestDto toDto(AdoptionRequest r) {
		Profile a = r.adopter;
		return new RequestDto(r.id, r.pet.id, r.status, new AdopterDto(a.id, a.fullName, a.avatarUrl, a.municipalityId), r.answers, r.motivation,
				r.aiSummary, r.aiScore, r.createdAt, r.decidedAt);
	}
}
