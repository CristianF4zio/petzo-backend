package com.petzo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.petzo.ai.AiService;
import com.petzo.domain.Repos.RequestRepo;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** RF-16: 3-line summary + 0-100 score for the publisher. Async; if the AI fails the request just has no summary. */
@Component
public class PreEvaluator {

	static final String SYSTEM = """
			Evalúas solicitudes de adopción de perros y gatos en Caracas para ayudar al publicador a decidir.
			Responde SOLO un objeto JSON: {"summary": "<máximo 3 líneas en español, concretas>", "score": <entero 0-100 de compatibilidad>}.
			Considera vivienda, otras mascotas, motivación y la ficha de la mascota. No inventes datos.""";

	private final AiService ai;
	private final RequestRepo requests;
	private final JdbcTemplate jdbc;
	private final TransactionTemplate read;

	public PreEvaluator(AiService ai, RequestRepo requests, JdbcTemplate jdbc, PlatformTransactionManager tx) {
		this.ai = ai;
		this.requests = requests;
		this.jdbc = jdbc;
		this.read = new TransactionTemplate(tx);
		this.read.setReadOnly(true);
	}

	// The AI call (up to ~90 s) runs outside any transaction so it holds no DB connection
	@Async
	public void run(UUID requestId) {
		String user = read.execute(s -> requests.findById(requestId).map(r -> "Mascota: " + Map.of("especie", r.pet.species, "nombre", r.pet.name,
				"edad_meses", String.valueOf(r.pet.ageMonths), "tamano", r.pet.size, "descripcion", String.valueOf(r.pet.description))
				+ "\nRespuestas del adoptante: " + r.answers + "\nMotivación: " + r.motivation).orElse(null));
		if (user == null) return;
		ai.askJson("pre_evaluation", "adoption_request", requestId, SYSTEM, user).ifPresent(j -> apply(requestId, j));
	}

	// Only the AI columns: saving the whole entity would undo an approve/reject made while the AI was thinking
	private void apply(UUID requestId, JsonNode j) {
		JsonNode summary = j.get("summary"), score = j.get("score");
		if (summary == null || !summary.isTextual() || score == null || !score.canConvertToInt()) return;
		int s = score.asInt();
		if (s < 0 || s > 100) return;
		String text = summary.asText().trim();
		jdbc.update("update adoption_requests set ai_summary = ?, ai_score = ? where id = ?", text.substring(0, Math.min(600, text.length())), (short) s, requestId);
	}
}
