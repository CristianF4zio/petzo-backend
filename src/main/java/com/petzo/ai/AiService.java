package com.petzo.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petzo.config.PetzoProperties;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Claude Haiku 5.5 for pre-evaluation and match. Short JSON out, validated by the caller.
 * Every call lands in ai_logs; over the daily token cap the AI is skipped (callers fall back to manual).
 */
@Service
public class AiService {

	private static final Logger log = LoggerFactory.getLogger(AiService.class);

	private final PetzoProperties props;
	private final JdbcTemplate jdbc;
	private final ObjectMapper json;
	private final AnthropicClient client;

	public AiService(PetzoProperties props, JdbcTemplate jdbc, ObjectMapper json) {
		this.props = props;
		this.jdbc = jdbc;
		this.json = json;
		this.client = props.anthropicApiKey() == null || props.anthropicApiKey().isBlank()
				? null
				: AnthropicOkHttpClient.builder().apiKey(props.anthropicApiKey()).timeout(Duration.ofSeconds(30)).build();
	}

	public long tokensToday() {
		Long used = jdbc.queryForObject("select coalesce(sum(input_tokens + output_tokens), 0) from ai_logs where created_at >= date_trunc('day', now())", Long.class);
		return used == null ? 0 : used;
	}

	public boolean available() { return client != null && tokensToday() < props.aiDailyTokenCap(); }

	/** task is an ai_task label: moderation | pre_evaluation | match | import. */
	public Optional<JsonNode> askJson(String task, String entityType, UUID entityId, String system, String user) {
		if (!available()) {
			log.warn("AI skipped for {} (no key or daily cap reached)", task);
			return Optional.empty();
		}
		try {
			// No temperature/top_p/prefill: Haiku 5.5 rejects them. Low effort keeps short JSON tasks cheap.
			Message res = client.messages().create(MessageCreateParams.builder()
					.model(props.aiModel())
					.maxTokens(2000L)
					.system(system)
					.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
					.addUserMessage(user)
					.build());
			String text = res.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text()).collect(Collectors.joining());
			boolean refused = res.stopReason().map(r -> r.equals(StopReason.REFUSAL)).orElse(false);
			JsonNode parsed = refused ? null : parse(text);
			record(task, entityType, entityId, res.usage().inputTokens(), res.usage().outputTokens(),
					parsed != null ? parsed : json.valueToTree(Map.of("error", refused ? "refusal" : "invalid_json", "raw", text.length() > 500 ? text.substring(0, 500) : text)));
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

	// The model sometimes wraps JSON in ``` fences; anything else is invalid
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
