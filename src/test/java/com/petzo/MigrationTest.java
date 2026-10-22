package com.petzo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MigrationTest {

	@Autowired JdbcTemplate jdbc;

	@Test
	void schemaSeedAndRls() {
		assertThat(jdbc.queryForObject("select count(*) from municipalities", Integer.class)).isEqualTo(11);
		assertThat(jdbc.queryForObject("select count(*) from pg_tables where schemaname = 'public' and not rowsecurity", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from pg_policies where schemaname = 'public' and cmd <> 'SELECT'", Integer.class)).isZero();
	}

	@Autowired com.petzo.ai.AiService ai;
	@Autowired com.fasterxml.jackson.databind.ObjectMapper json;

	@Test
	void aiLogsCountTowardTheDailyCap() {
		long before = ai.tokensToday();
		ai.record("pre_evaluation", "adoption_request", UUID.randomUUID(), 1200, 150, json.createObjectNode().put("score", 80));
		ai.record("import", "import_job", UUID.randomUUID(), 100, 50, json.createObjectNode());
		assertThat(ai.tokensToday()).isEqualTo(before + 1500);
		assertThat(ai.available()).isFalse(); // no API key in tests → AI off, callers fall back to manual
	}

	@Test
	void signUpCreatesProfileWithSanitizedMetadata() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into auth.users (id, raw_user_meta_data) values (?, ?::jsonb)", id,
				"{\"full_name\": \"  Andreína  \", \"avatar_url\": \"javascript:alert(1)\"}");
		var profile = jdbc.queryForMap("select full_name, avatar_url, plan::text from profiles where id = ?", id);
		assertThat(profile).containsEntry("full_name", "Andreína").containsEntry("avatar_url", null).containsEntry("plan", "free");
	}
}
