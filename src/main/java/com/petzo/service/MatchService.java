package com.petzo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petzo.ai.AiService;
import com.petzo.common.ApiException;
import com.petzo.domain.Pet;
import com.petzo.domain.Profile;
import com.petzo.domain.Repos.LifestyleRepo;
import com.petzo.domain.Repos.PetRepo;
import com.petzo.domain.Simple.LifestyleProfile;
import com.petzo.domain.Types.PetStatus;
import com.petzo.web.Dtos.*;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-12/13: questionnaire + up to 10 compatible pets with % and reason. Not stored; logged in ai_logs. */
@Service
@Transactional
public class MatchService {

	static final String SYSTEM = """
			Recomiendas perros y gatos en adopción según el estilo de vida del adoptante.
			Responde SOLO un objeto JSON: {"matches": [{"petId": "<id exacto de la lista>", "score": <entero 0-100>, "reason": "<una línea en español>"}]}.
			Máximo 10, ordenados de mayor a menor score. Usa solo ids de la lista.""";

	private final LifestyleRepo lifestyles;
	private final PetRepo pets;
	private final AiService ai;
	private final PetService petService;
	private final ObjectMapper json;

	public MatchService(LifestyleRepo lifestyles, PetRepo pets, AiService ai, PetService petService, ObjectMapper json) {
		this.lifestyles = lifestyles;
		this.pets = pets;
		this.ai = ai;
		this.petService = petService;
		this.json = json;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> getLifestyle(Profile me) {
		return lifestyles.findById(me.id).map(l -> l.answers).orElse(null);
	}

	public Map<String, Object> saveLifestyle(Profile me, Lifestyle in) {
		LifestyleProfile l = lifestyles.findById(me.id).orElseGet(LifestyleProfile::new);
		l.userId = me.id;
		l.answers = json.convertValue(in, Map.class);
		return lifestyles.save(l).answers;
	}

	public MatchResult match(Profile me, MatchInput in) {
		Map<String, Object> answers = getLifestyle(me);
		if (answers == null) throw ApiException.conflict("LIFESTYLE_REQUIRED", "Responde el cuestionario primero");

		// Pre-filter in SQL so the prompt stays short (max 20 candidates)
		Specification<Pet> spec = (root, q, cb) -> {
			List<Predicate> p = new ArrayList<>();
			p.add(cb.equal(root.get("status"), PetStatus.approved));
			p.add(cb.isFalse(root.get("owner").get("suspended")));
			p.add(cb.notEqual(root.get("owner").get("id"), me.id));
			if (in.species() != null) p.add(cb.equal(root.get("species"), in.species()));
			if (in.size() != null) p.add(cb.equal(root.get("size"), in.size()));
			if (in.municipalityId() != null) p.add(cb.equal(root.get("municipalityId"), in.municipalityId()));
			return cb.and(p.toArray(Predicate[]::new));
		};
		List<Pet> candidates = pets.findAll(spec, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
		if (candidates.isEmpty()) return new MatchResult(List.of(), true);

		StringBuilder list = new StringBuilder();
		for (Pet p : candidates) {
			String desc = p.description == null ? "" : p.description.substring(0, Math.min(200, p.description.length()));
			list.append(p.id).append(" | ").append(p.species).append(", ").append(p.ageMonths).append(" meses, ").append(p.sex).append(", ")
					.append(p.size).append(", vacunado=").append(p.vaccinated).append(", esterilizado=").append(p.sterilized).append(" | ").append(desc).append('\n');
		}
		var result = ai.askJson("match", "profile", me.id, SYSTEM, "Estilo de vida: " + answers + "\nCandidatas:\n" + list);
		if (result.isEmpty()) return new MatchResult(List.of(), false);

		Map<UUID, Pet> byId = new LinkedHashMap<>();
		candidates.forEach(p -> byId.put(p.id, p));
		List<MatchDto> matches = new ArrayList<>();
		JsonNode arr = result.get().get("matches");
		if (arr != null && arr.isArray()) {
			for (JsonNode m : arr) {
				if (matches.size() == 10) break;
				try {
					Pet pet = byId.remove(UUID.fromString(m.path("petId").asText()));
					int score = m.path("score").asInt(-1);
					if (pet == null || score < 0 || score > 100) continue; // ignore ids the model invented
					String reason = m.path("reason").asText("");
					matches.add(new MatchDto(petService.toDto(pet, false, null), score, reason.substring(0, Math.min(200, reason.length()))));
				} catch (IllegalArgumentException ignored) {
					// not a UUID
				}
			}
		}
		return new MatchResult(matches, true);
	}
}
