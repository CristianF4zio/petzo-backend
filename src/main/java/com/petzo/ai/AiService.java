package com.petzo.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petzo.config.PetzoProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Pre-evaluation and match through OpenRouter's OpenAI-compatible API (D-29; model in AI_MODEL). Short JSON out, validated
 * by the caller. Every call lands in ai_logs; over the daily token cap the AI is skipped (callers fall back to manual).
 */
@Service
public class AiService {

	private static final Logger log = LoggerFactory.getLogger(AiService.class);

	private final PetzoProperties props;
	private final JdbcTemplate jdbc;
	private final ObjectMapper json;
	private final RestClient client;

	public AiService(PetzoProperties props, JdbcTemplate jdbc, ObjectMapper json) {
		this.props = props;
		this.jdbc = jdbc;
		this.json = json;
		this.client = props.openrouterApiKey() == null || props.openrouterApiKey().isBlank()
				? null
				: RestClient.builder().baseUrl("https://openrouter.ai/api/v1").requestFactory(com.petzo.config.WebConfig.timeouts(Duration.ofSeconds(30)))
						.defaultHeader("Authorization", "Bearer " + props.openrouterApiKey()).build();
	}

	public long tokensToday() {
		Long used = jdbc.queryForObject("select coalesce(sum(input_tokens + output_tokens), 0) from ai_logs where created_at >= date_trunc('day', now())", Long.class);
		return used == null ? 0 : used;
	}

	public boolean available() { return client != null && underDailyCap(); }

	/** RNF-05: shared by every AI caller, including n8n moderation (which calls OpenRouter with its own credential). */
	public boolean underDailyCap() { return tokensToday() < props.aiDailyTokenCap(); }

	/** task is an ai_task label: moderation | pre_evaluation | match | import. */
	public Optional<JsonNode> askJson(String task, String entityType, UUID entityId, String system, String user) {
		if (!available()) {
			log.warn("AI skipped for {} (no key or daily cap reached)", task);
			return Optional.empty();
		}
		try {
			JsonNode res = client.post().uri("/chat/completions").body(Map.of(
					"model", props.aiModel(),
					"max_tokens", 800,
					"response_format", Map.of("type", "json_object"),
					"messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user))))
					.retrieve().body(JsonNode.class);
			String text = res.path("choices").path(0).path("message").path("content").asText("");
			JsonNode parsed = parse(text);
			JsonNode usage = res.path("usage");
			record(task, entityType, entityId, usage.path("prompt_tokens").asLong(), usage.path("completion_tokens").asLong(),
					parsed != null ? parsed : json.valueToTree(Map.of("error", "invalid_json", "raw", text.length() > 500 ? text.substring(0, 500) : text)));
			return Optional.ofNullable(parsed);
		} catch (Exception e) {
			log.warn("AI call failed for {}: {}", task, e.getMessage());
			record(task, entityType, entityId, 0, 0, json.valueToTree(Map.of("error", String.valueOf(e.getMessage()))));
			return Optional.empty();
		}
	}

	/** Also used by /internal callbacks (n8n moderation) to log tokens spent outside the API. */
	public void record(String task, String entityType, UUID entityId, long input, long output, JsonNode result) {
		jdbc.update("insert into ai_logs (task, entity_type, entity_id, model, input_tokens, output_tokens, result) values (?::ai_task, ?, ?, ?, ?, ?, ?::jsonb)",
				task, entityType, entityId, props.aiModel(), input, output, result.toString());
	}

	// Some models still wrap JSON in ``` fences; anything else is invalid
	private JsonNode parse(String text) {
		String body = text.trim().replaceAll("^```(?:json)?\\s*|\\s*```$", "");
		try {
			JsonNode node = json.readTree(body);
			return node != null && node.isObject() ? node : null;
		} catch (Exception e) {
			return null;
		}
	}
}
