package com.petzo;

import static com.petzo.ApiTest.as;
import static com.petzo.ApiTest.withJson;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petzo.storage.StorageClient;
import com.petzo.storage.StorageClient.SignedUpload;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InternalTest {

	static final String SECRET = "test-internal-secret";

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper json;
	@MockitoBean StorageClient storage;

	@BeforeEach
	void stubs() {
		when(storage.signUpload(anyString(), anyBoolean())).thenAnswer(i -> new SignedUpload(i.getArgument(0), "https://signed", "tok"));
		when(storage.exists(anyString())).thenReturn(true);
		when(storage.publicUrl(anyString())).thenAnswer(i -> "https://cdn/" + i.getArgument(0));
	}

	UUID user(boolean admin) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into auth.users (id, raw_user_meta_data) values (?, '{\"full_name\":\"U\"}'::jsonb)", id);
		jdbc.update("update profiles set whatsapp = '+584121234567', municipality_id = 2, role = ?::user_role where id = ?", admin ? "admin" : "user", id);
		return id;
	}

	String pendingPet(UUID owner) throws Exception {
		String id = json.readTree(mvc.perform(withJson(post("/api/pets"), ApiTest.PET).with(as(owner))).andReturn().getResponse().getContentAsString()).get("id").asText();
		mvc.perform(withJson(post("/api/pets/" + id + "/photos"), "{\"path\":\"pets/" + id + "/" + UUID.randomUUID() + ".jpg\"}").with(as(owner)));
		return id;
	}

	String updatedAt(String petId) {
		return String.valueOf(jdbc.queryForObject("select updated_at from pets where id = ?::uuid", java.sql.Timestamp.class, petId).toInstant());
	}

	@Test
	void secretIsRequired() throws Exception {
		mvc.perform(get("/internal/imports/next")).andExpect(status().isUnauthorized());
		mvc.perform(get("/internal/imports/next").header("x-internal-secret", "wrong")).andExpect(status().isUnauthorized());
		mvc.perform(get("/internal/imports/next").header("x-internal-secret", SECRET)).andExpect(status().is2xxSuccessful());
	}

	@Test
	void moderationApprovesLowRiskAndKeepsHighRiskInQueue() throws Exception {
		UUID owner = user(false);
		String low = pendingPet(owner), high = pendingPet(owner);
		mvc.perform(withJson(post("/internal/pets/" + low + "/moderation-result"), "{\"riskScore\":10,\"reason\":\"ok\",\"updatedAt\":\"" + updatedAt(low) + "\",\"inputTokens\":900,\"outputTokens\":60}")
				.header("x-internal-secret", SECRET)).andExpect(jsonPath("$.status").value("approved"));
		mvc.perform(withJson(post("/internal/pets/" + high + "/moderation-result"), "{\"riskScore\":85,\"reason\":\"Foto genérica de internet\",\"updatedAt\":\"" + updatedAt(high) + "\",\"inputTokens\":900,\"outputTokens\":60}")
				.header("x-internal-secret", SECRET)).andExpect(jsonPath("$.status").value("pending_review"));
		mvc.perform(get("/api/me/pets").with(as(owner))).andExpect(jsonPath("$[?(@.id=='" + high + "')].moderationReason").value("Foto genérica de internet"));
		// stale result (pet edited after n8n read it) is ignored
		mvc.perform(withJson(post("/internal/pets/" + high + "/moderation-result"), "{\"riskScore\":5,\"reason\":\"ok\",\"updatedAt\":\"2000-01-01T00:00:00Z\",\"inputTokens\":1,\"outputTokens\":1}")
				.header("x-internal-secret", SECRET)).andExpect(jsonPath("$.status").value("ignored"));
	}

	@Test
	void importJobLifecycle() throws Exception {
		UUID admin = user(true), plain = user(false);
		String handle = "refugio" + System.nanoTime() % 100000;
		mvc.perform(withJson(post("/api/admin/imports"), "{\"instagramHandle\":\"@" + handle + "\"}").with(as(plain))).andExpect(status().isForbidden());
		String job = json.readTree(mvc.perform(withJson(post("/api/admin/imports"), "{\"instagramHandle\":\"@" + handle + "\"}").with(as(admin)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
		mvc.perform(get("/internal/imports/next").header("x-internal-secret", SECRET)).andExpect(jsonPath("$.instagramHandle").value(handle));
		mvc.perform(withJson(post("/internal/imports/" + job + "/pets"),
				"{\"pets\":[{\"name\":\"Canela\",\"species\":\"dog\",\"description\":\"Rescatada\",\"photoUrls\":[\"http://evil.test/x.jpg\"],\"sourceUrl\":\"https://instagram.com/p/1\"}]}")
				.header("x-internal-secret", SECRET)).andExpect(jsonPath("$.created").value(1));
		mvc.perform(withJson(post("/internal/imports/" + job + "/status"), "{\"status\":\"done\",\"inputTokens\":50000,\"outputTokens\":2000}").header("x-internal-secret", SECRET))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/admin/imports").with(as(admin))).andExpect(jsonPath("$[?(@.id=='" + job + "')].petsCreated").value(1));
		mvc.perform(get("/api/me/pets").with(as(admin))).andExpect(jsonPath("$[0].status").value("draft")).andExpect(jsonPath("$[0].source").value("instagram"));
		verify(storage, never()).upload(anyString(), any(), anyString()); // non-Instagram URL rejected (SSRF guard)
	}
}
