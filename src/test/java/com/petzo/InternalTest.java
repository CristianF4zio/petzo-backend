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
		mvc.perform(withJson(patch("/api/pets/" + id + "/status"), "{\"status\":\"pending_review\"}").with(as(owner)));
		return id;
	}

	int version(String petId) {
		return jdbc.queryForObject("select version from pets where id = ?::uuid", Integer.class, petId);
	}

	@Test
	void secretIsRequired() throws Exception {
		// encoded path must not bypass the secret (decoded by MVC, checked by the same matcher)
		mvc.perform(get(java.net.URI.create("/%69nternal/imports/next"))).andExpect(status().is4xxClientError());
		mvc.perform(get("/internal/imports/next")).andExpect(status().isUnauthorized());
		mvc.perform(get("/internal/imports/next").header("x-internal-secret", "wrong")).andExpect(status().isUnauthorized());
		mvc.perform(get("/internal/imports/next").header("x-internal-secret", SECRET)).andExpect(status().is2xxSuccessful());
	}

	@Test
	void moderationApprovesLowRiskAndKeepsHighRiskInQueue() throws Exception {
		UUID owner = user(false);
		String low = pendingPet(owner), high = pendingPet(owner);
		mvc.perform(withJson(post("/internal/pets/" + low + "/moderation-result"), "{\"riskScore\":10,\"reason\":\"ok\",\"version\":" + version(low) + ",\"inputTokens\":900,\"outputTokens\":60}")
				.header("x-internal-secret", SECRET)).andExpect(jsonPath("$.status").value("approved"));
		mvc.perform(withJson(post("/internal/pets/" + high + "/moderation-result"), "{\"riskScore\":85,\"reason\":\"Foto genérica de internet\",\"version\":" + version(high) + ",\"inputTokens\":900,\"outputTokens\":60}")
				.header("x-internal-secret", SECRET)).andExpect(jsonPath("$.status").value("pending_review"));
		mvc.perform(get("/api/me/pets").with(as(owner))).andExpect(jsonPath("$[?(@.id=='" + high + "')].moderationReason").value("Foto genérica de internet"));
		// stale result (pet edited after n8n read it) is ignored
		mvc.perform(withJson(post("/internal/pets/" + high + "/moderation-result"), "{\"riskScore\":5,\"reason\":\"ok\",\"version\":999,\"inputTokens\":1,\"outputTokens\":1}")
				.header("x-internal-secret", SECRET)).andExpect(jsonPath("$.status").value("ignored"));
	}

	@Test
	void photoChangesBumpVersionSoOldModerationIsIgnored() throws Exception {
		UUID owner = user(false), boss = user(true);
		String pet = pendingPet(owner);
		int before = version(pet);
		mvc.perform(withJson(post("/api/pets/" + pet + "/photos"), "{\"path\":\"pets/" + pet + "/" + UUID.randomUUID() + ".jpg\"}").with(as(owner)));
		org.assertj.core.api.Assertions.assertThat(version(pet)).isGreaterThan(before);
		mvc.perform(withJson(post("/internal/pets/" + pet + "/moderation-result"), "{\"riskScore\":5,\"reason\":\"ok\",\"version\":" + before + ",\"inputTokens\":1,\"outputTokens\":1}")
				.header("x-internal-secret", SECRET)).andExpect(jsonPath("$.status").value("ignored"));
		// admin: stale version and non-pending pets are refused
		mvc.perform(post("/api/admin/pets/" + pet + "/approve?version=" + before).with(as(boss))).andExpect(jsonPath("$.code").value("STALE"));
		mvc.perform(post("/api/admin/pets/" + pet + "/approve?version=" + version(pet)).with(as(boss))).andExpect(jsonPath("$.status").value("approved"));
		mvc.perform(post("/api/admin/pets/" + pet + "/approve").with(as(boss))).andExpect(jsonPath("$.code").value("INVALID_STATUS"));
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
		String draft = json.readTree(mvc.perform(get("/api/me/pets").with(as(admin))).andExpect(jsonPath("$[0].status").value("draft"))
				.andExpect(jsonPath("$[0].source").value("instagram")).andReturn().getResponse().getContentAsString()).get(0).get("id").asText();
		verify(storage, never()).upload(anyString(), any(), anyString()); // non-Instagram URL rejected (SSRF guard)
		mvc.perform(delete("/api/pets/" + draft).with(as(plain))).andExpect(status().isNotFound());
		mvc.perform(delete("/api/pets/" + draft).with(as(admin))).andExpect(status().isNoContent());
		mvc.perform(get("/api/pets/" + draft).with(as(admin))).andExpect(status().isNotFound());
	}
}
