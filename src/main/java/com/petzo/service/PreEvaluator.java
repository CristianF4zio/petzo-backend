package com.petzo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.petzo.ai.AiService;
import com.petzo.domain.AdoptionRequest;
import com.petzo.domain.Repos.RequestRepo;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** RF-16: 3-line summary + 0-100 score for the publisher. Async; if the AI fails the request just has no summary. */
@Component
public class PreEvaluator {

	static final String SYSTEM = """
			Evalúas solicitudes de adopción de perros y gatos en Caracas para ayudar al publicador a decidir.
			Responde SOLO un objeto JSON: {"summary": "<máximo 3 líneas en español, concretas>", "score": <entero 0-100 de compatibilidad>}.
			Considera vivienda, otras mascotas, motivación y la ficha de la mascota. No inventes datos.""";

	private final AiService ai;
	private final RequestRepo requests;

	public PreEvaluator(AiService ai, RequestRepo requests) {
		this.ai = ai;
		this.requests = requests;
	}

	@Async
	@Transactional
	public void run(UUID requestId) {
		AdoptionRequest r = requests.findById(requestId).orElse(null);
		if (r == null) return;
		String user = "Mascota: " + Map.of("especie", r.pet.species, "nombre", r.pet.name, "edad_meses", r.pet.ageMonths, "tamano", r.pet.size,
				"descripcion", String.valueOf(r.pet.description)) + "\nRespuestas del adoptante: " + r.answers + "\nMotivación: " + r.motivation;
		ai.askJson("pre_evaluation", "adoption_request", r.id, SYSTEM, user).ifPresent(j -> apply(r, j));
	}

	private void apply(AdoptionRequest r, JsonNode j) {
		JsonNode summary = j.get("summary"), score = j.get("score");
		if (summary == null || !summary.isTextual() || score == null || !score.canConvertToInt()) return;
		int s = score.asInt();
		if (s < 0 || s > 100) return;
		r.aiSummary = summary.asText().trim().substring(0, Math.min(600, summary.asText().trim().length()));
		r.aiScore = (short) s;
		requests.save(r);
	}
}
