package com.petzo;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petzo.storage.StorageClient;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper json;
	@MockitoBean StorageClient storage;

	static final String PET = """
			{"species":"dog","name":"Toby","ageMonths":14,"sex":"male","size":"medium","municipalityId":2,"vaccinated":true,"description":"Tranquilo y cariñoso"}""";

	@BeforeEach
	void storageStub() {
		when(storage.exists(anyString())).thenReturn(true);
		when(storage.publicUrl(anyString())).thenAnswer(i -> "https://cdn/" + i.getArgument(0));
	}

	// ---- helpers ----

	// smallest bytes that pass the JPEG magic-number check
	static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16 };

	static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder upload(String petId) {
		return multipart("/api/pets/" + petId + "/photos/upload").file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG));
	}

	UUID user(String name, boolean complete) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into auth.users (id, raw_user_meta_data) values (?, ?::jsonb)", id, "{\"full_name\":\"" + name + "\"}");
		if (complete) jdbc.update("update profiles set whatsapp = '+584121234567', municipality_id = 2 where id = ?", id);
		return id;
	}

	public static RequestPostProcessor as(UUID id) {
		return jwt().jwt(j -> j.subject(id.toString()).claim("role", "authenticated"));
	}

	JsonNode call(MockHttpServletRequestBuilder req, int status) throws Exception {
		String body = mvc.perform(req).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
		return body.isEmpty() ? null : json.readTree(body);
	}

	public static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder req, String body) {
		return req.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	/** PUT /photos body with n freshly "uploaded" paths of that pet. */
	public static String gallery(String petId, int n) {
		var paths = new java.util.ArrayList<String>();
		for (int i = 0; i < n; i++) paths.add("\"pets/" + petId + "/" + UUID.randomUUID() + ".jpg\"");
		return "{\"paths\":[" + String.join(",", paths) + "]}";
	}

	/** Creates a pet with one photo (pending_review), the way the app does it (D-28). */
	String publish(UUID owner) throws Exception {
		String id = call(withJson(post("/api/pets"), PET).with(as(owner)), 201).get("id").asText();
		call(withJson(put("/api/pets/" + id + "/photos"), gallery(id, 1)).with(as(owner)), 200);
		call(withJson(patch("/api/pets/" + id + "/status"), "{\"status\":\"pending_review\"}").with(as(owner)), 200);
		return id;
	}

	void approve(String petId) { jdbc.update("update pets set status = 'approved' where id = ?::uuid", petId); }

	// ---- tests ----

	@Test
	void publicEndpointsAndAuth() throws Exception {
		call(get("/api/municipalities"), 200);
		mvc.perform(get("/api/municipalities")).andExpect(jsonPath("$.length()").value(11));
		call(get("/api/pets"), 200);
		call(get("/api/pets?size=huge"), 422);
		call(get("/api/me"), 401);
		mvc.perform(get("/api/me").with(as(UUID.randomUUID()))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void profileCompletion() throws Exception {
		UUID u = user("Ana", false);
		mvc.perform(get("/api/me").with(as(u))).andExpect(jsonPath("$.fullName").value("Ana")).andExpect(jsonPath("$.complete").value(false));
		mvc.perform(withJson(post("/api/pets"), PET).with(as(u))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROFILE_INCOMPLETE"));
		call(withJson(put("/api/me"), "{\"fullName\":\"Ana\",\"whatsapp\":\"12345\",\"municipalityId\":2}").with(as(u)), 422);
		call(withJson(put("/api/me"), "{\"fullName\":\"Ana\",\"whatsapp\":\"0414 765 4321\",\"municipalityId\":999}").with(as(u)), 422);
		mvc.perform(withJson(put("/api/me"), "{\"fullName\":\"Ana\",\"whatsapp\":\"+58 0412-123 4567\",\"municipalityId\":3}").with(as(u)))
				.andExpect(jsonPath("$.whatsapp").value("+584121234567")).andExpect(jsonPath("$.complete").value(true));
	}

	@Test
	void publishModerateAndCatalog() throws Exception {
		UUID owner = user("Owner", true), other = user("Other", true);
		call(withJson(post("/api/pets"), PET.replace("dog", "cow")).with(as(owner)), 422);
		String id = publish(owner);
		call(withJson(put("/api/pets/" + id + "/photos"), "{\"paths\":[\"pets/other/x.jpg\"]}").with(as(owner)), 422);
		call(upload(id).with(as(other)), 404);

		call(get("/api/pets/" + id).with(as(other)), 404); // pending is private
		mvc.perform(get("/api/pets/" + id).with(as(owner))).andExpect(jsonPath("$.status").value("pending_review"));

		approve(id);
		mvc.perform(get("/api/pets?species=dog&size=small,medium&municipalityId=2&ageMin=12"))
				.andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").exists())
				.andExpect(jsonPath("$.items[0].moderationReason").doesNotExist())
				.andExpect(jsonPath("$.items[0].publisher.name").value("Owner"));
		mvc.perform(get("/api/pets/" + id)).andExpect(jsonPath("$.publisher.petsCount").value(1)).andExpect(jsonPath("$.publisher.whatsapp").doesNotExist());
		mvc.perform(get("/api/pets?species=cat")).andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").doesNotExist());
	}

	@Test
	void ownershipEditsAndTransitions() throws Exception {
		UUID owner = user("Owner", true), other = user("Other", true);
		String id = publish(owner);
		call(withJson(put("/api/pets/" + id), PET).with(as(other)), 404);
		call(withJson(patch("/api/pets/" + id + "/status"), "{\"status\":\"paused\"}").with(as(owner)), 409); // still in review

		approve(id);
		call(withJson(patch("/api/pets/" + id + "/status"), "{\"status\":\"paused\"}").with(as(owner)), 200);
		// paused → edit must not resume without moderation
		mvc.perform(withJson(put("/api/pets/" + id), PET.replace("Tranquilo", "Vendo")).with(as(owner))).andExpect(jsonPath("$.status").value("pending_review"));
		mvc.perform(withJson(patch("/api/pets/" + id + "/status"), "{\"status\":\"adopted\"}").with(as(owner))).andExpect(jsonPath("$.status").value("adopted"));
		call(withJson(put("/api/pets/" + id), PET).with(as(owner)), 409);
	}

	@Test
	void planLimitAndAdoptionFreesSlot() throws Exception {
		UUID owner = user("Owner", true);
		String first = publish(owner);
		publish(owner);
		publish(owner);
		mvc.perform(withJson(post("/api/pets"), PET).with(as(owner))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_LIMIT"));
		call(withJson(patch("/api/pets/" + first + "/status"), "{\"status\":\"adopted\"}").with(as(owner)), 200);
		call(withJson(post("/api/pets"), PET).with(as(owner)), 201);
	}

	@Test
	void photosLimitsAndDeletion() throws Exception {
		UUID owner = user("Owner", true);
		String id = publish(owner);
		call(withJson(put("/api/pets/" + id + "/photos"), gallery(id, 6)).with(as(owner)), 422); // max 5
		call(withJson(put("/api/pets/" + id + "/photos"), gallery(id, 5)).with(as(owner)), 200);
		mvc.perform(upload(id).with(as(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.path").value(org.hamcrest.Matchers.startsWith("pets/" + id + "/"))); // a full gallery can still upload a replacement
		verify(storage).upload(startsWith("pets/" + id + "/"), any(), eq("image/jpeg"));
		call(multipart("/api/pets/" + id + "/photos/upload").file(new MockMultipartFile("file", "x.png", "image/png", "not a jpeg".getBytes())).with(as(owner)), 422);
		verify(storage).deleteAfterCommit(argThat(l -> l.size() == 1)); // the replaced photo leaves Storage
		mvc.perform(delete("/api/pets/" + id).with(as(owner))).andExpect(jsonPath("$.code").value("INVALID_STATUS")); // only drafts
	}

	@Test
	void replaceGalleryInOneStep() throws Exception {
		UUID owner = user("Owner", true);
		String id = publish(owner);
		call(withJson(put("/api/pets/" + id + "/photos"), gallery(id, 5)).with(as(owner)), 200);
		approve(id);
		var paths = new java.util.ArrayList<String>();
		call(get("/api/pets/" + id).with(as(owner)), 200).get("photos").forEach(p -> paths.add(p.get("path").asText()));
		String fresh = "pets/" + id + "/" + UUID.randomUUID() + ".jpg";
		String body = "{\"paths\":[\"" + fresh + "\",\"" + String.join("\",\"", paths.subList(1, 5)) + "\"]}"; // swap one out of a full gallery
		call(withJson(put("/api/pets/" + id + "/photos"), body).with(as(user("Other", true))), 404);
		call(withJson(put("/api/pets/" + id + "/photos"), "{\"paths\":[\"pets/other/x.jpg\"]}").with(as(owner)), 422);
		call(withJson(put("/api/pets/" + id + "/photos"), "{\"paths\":[\"" + fresh + "\",\"" + fresh + "\"]}").with(as(owner)), 422);
		call(withJson(put("/api/pets/" + id + "/photos"), "{\"paths\":[]}").with(as(owner)), 422);
		clearInvocations(storage);
		mvc.perform(withJson(put("/api/pets/" + id + "/photos"), body).with(as(owner)))
				.andExpect(jsonPath("$.photos.length()").value(5)).andExpect(jsonPath("$.photos[0].path").value(fresh))
				.andExpect(jsonPath("$.status").value("pending_review")); // content changed → moderated again
		verify(storage).deleteAfterCommit(java.util.List.of(paths.get(0)));
		// order only: still one call, no new photos needed
		String reversed = "{\"paths\":[\"" + paths.get(4) + "\",\"" + paths.get(3) + "\",\"" + paths.get(2) + "\",\"" + paths.get(1) + "\",\"" + fresh + "\"]}";
		mvc.perform(withJson(put("/api/pets/" + id + "/photos"), reversed).with(as(owner))).andExpect(jsonPath("$.photos[0].path").value(paths.get(4)));
	}

	@Test
	void newPetIsDraftUntilSubmitted() throws Exception {
		UUID owner = user("Owner", true);
		String id = call(withJson(post("/api/pets"), PET).with(as(owner)), 201).get("id").asText();
		mvc.perform(get("/api/pets/" + id).with(as(owner))).andExpect(jsonPath("$.status").value("draft"));
		mvc.perform(withJson(patch("/api/pets/" + id + "/status"), "{\"status\":\"pending_review\"}").with(as(owner)))
				.andExpect(jsonPath("$.code").value("PHOTOS_REQUIRED"));
		call(delete("/api/pets/" + id).with(as(owner)), 204); // abandoned upload: the draft can go
		call(get("/api/pets/" + id).with(as(owner)), 404);
	}

	@Test
	void favoritesAndReports() throws Exception {
		UUID owner = user("Owner", true), fan = user("Fan", true);
		String id = publish(owner);
		call(post("/api/favorites/" + id).with(as(fan)), 404); // not approved yet
		approve(id);
		call(post("/api/favorites/" + id).with(as(fan)), 204);
		call(post("/api/favorites/" + id).with(as(fan)), 204);
		mvc.perform(get("/api/me/favorites").with(as(fan))).andExpect(jsonPath("$.length()").value(1));
		call(delete("/api/favorites/" + id).with(as(fan)), 204);
		mvc.perform(get("/api/me/favorites").with(as(fan))).andExpect(jsonPath("$.length()").value(0));
		call(withJson(post("/api/pets/" + id + "/reports"), "{\"reason\":\"x\"}").with(as(fan)), 422);
		call(withJson(post("/api/pets/" + id + "/reports"), "{\"reason\":\"Fotos de internet\"}").with(as(fan)), 201);
	}

	@Test
	void adminFlows() throws Exception {
		UUID owner = user("Owner", true), boss = user("Boss", true);
		String id = publish(owner);
		call(get("/api/admin/moderation").with(as(owner)), 403);
		jdbc.update("update profiles set role = 'admin' where id = ?", boss);
		mvc.perform(get("/api/admin/moderation").with(as(boss))).andExpect(jsonPath("$[?(@.id=='" + id + "')]").exists());

		mvc.perform(withJson(post("/api/admin/pets/" + id + "/reject"), "{\"reason\":\"Fotos de internet\"}").with(as(boss)))
				.andExpect(jsonPath("$.status").value("rejected")).andExpect(jsonPath("$.photos.length()").value(0));
		verify(storage).deleteAfterCommit(argThat(l -> l.size() == 1));
		mvc.perform(get("/api/me/pets").with(as(owner))).andExpect(jsonPath("$[0].moderationReason").value("Fotos de internet"));

		call(withJson(post("/api/me/foundation-request"), "{\"foundationName\":\"Patas del Ávila\",\"foundationInstagram\":\"@patas\"}").with(as(owner)), 200);
		mvc.perform(get("/api/admin/foundations").with(as(boss))).andExpect(jsonPath("$[?(@.id=='" + owner + "')]").exists());
		mvc.perform(post("/api/admin/foundations/" + owner + "/verify").with(as(boss))).andExpect(jsonPath("$.plan").value("pro"));

		String live = publish(owner);
		approve(live);
		call(withJson(put("/api/admin/users/" + owner + "/suspend"), "{\"suspended\":true}").with(as(boss)), 200);
		mvc.perform(get("/api/me").with(as(owner))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SUSPENDED"));
		call(get("/api/pets/" + live), 404);
		mvc.perform(get("/api/admin/stats").with(as(boss))).andExpect(jsonPath("$.users").exists());
	}
}
