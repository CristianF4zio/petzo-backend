package com.petzo;

import static com.petzo.ApiTest.as;
import static com.petzo.ApiTest.withJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petzo.ai.AiService;
import com.petzo.storage.StorageClient;
import java.util.Optional;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RequestTest {

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper json;
	@MockitoBean StorageClient storage;
	@MockitoBean AiService ai;

	static final String REQUEST = "{\"answers\":{\"vivienda\":\"Casa\",\"otrasMascotas\":\"No\"},\"motivation\":\"Tengo patio grande y tiempo para pasearlo cada día.\"}";

	@BeforeEach
	void stubs() {
		when(storage.exists(anyString())).thenReturn(true);
		when(storage.publicUrl(anyString())).thenAnswer(i -> "https://cdn/" + i.getArgument(0));
	}

	UUID user(String name, boolean complete, String wa) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into auth.users (id, raw_user_meta_data) values (?, ?::jsonb)", id, "{\"full_name\":\"" + name + "\"}");
		if (complete) jdbc.update("update profiles set whatsapp = ?, municipality_id = 2 where id = ?", wa, id);
		return id;
	}

	String approvedPet(UUID owner) throws Exception {
		String id = call(withJson(post("/api/pets"), ApiTest.PET).with(as(owner)), 201).get("id").asText();
		call(withJson(put("/api/pets/" + id + "/photos"), ApiTest.gallery(id, 1)).with(as(owner)), 200);
		jdbc.update("update pets set status = 'approved' where id = ?::uuid", id);
		return id;
	}

	JsonNode call(MockHttpServletRequestBuilder req, int status) throws Exception {
		String body = mvc.perform(req).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
		return body.isEmpty() ? null : json.readTree(body);
	}

	JsonNode awaitRequests(String petId, UUID owner) throws Exception {
		for (int i = 0; i < 50; i++) {
			JsonNode list = call(get("/api/pets/" + petId + "/requests").with(as(owner)), 200);
			if (!list.get(0).get("aiSummary").isNull()) return list;
			Thread.sleep(100);
		}
		return call(get("/api/pets/" + petId + "/requests").with(as(owner)), 200);
	}

	@Test
	void requestApproveAndContact() throws Exception {
		when(ai.askJson(eq("pre_evaluation"), any(), any(), any(), any()))
				.thenReturn(Optional.of(json.readTree("{\"summary\":\"Casa con patio, sin otras mascotas.\",\"score\":82}")));
		UUID owner = user("Owner", true, "+584121111111"), adopter = user("Adopter", true, "+584142222222"), other = user("Other", true, "+584163333333");
		UUID incomplete = user("Nuevo", false, null);
		String pet = approvedPet(owner);

		mvc.perform(withJson(post("/api/pets/" + pet + "/requests"), REQUEST).with(as(incomplete))).andExpect(jsonPath("$.code").value("PROFILE_INCOMPLETE"));
		mvc.perform(withJson(post("/api/pets/" + pet + "/requests"), REQUEST).with(as(owner))).andExpect(jsonPath("$.code").value("OWN_PET"));
		call(withJson(post("/api/pets/" + pet + "/requests"), "{\"answers\":{},\"motivation\":\"corta\"}").with(as(adopter)), 422);
		String req = call(withJson(post("/api/pets/" + pet + "/requests"), REQUEST).with(as(adopter)), 201).get("id").asText();
		mvc.perform(withJson(post("/api/pets/" + pet + "/requests"), REQUEST).with(as(adopter))).andExpect(jsonPath("$.code").value("ALREADY_REQUESTED"));
		String otherReq = call(withJson(post("/api/pets/" + pet + "/requests"), REQUEST).with(as(other)), 201).get("id").asText();

		JsonNode list = awaitRequests(pet, owner);
		assertThat(list.findValuesAsText("aiSummary")).contains("Casa con patio, sin otras mascotas.");
		call(get("/api/pets/" + pet + "/requests").with(as(adopter)), 404);

		mvc.perform(get("/api/requests/" + req + "/contact").with(as(adopter))).andExpect(jsonPath("$.code").value("NOT_APPROVED"));
		call(post("/api/requests/" + req + "/approve").with(as(adopter)), 404);
		call(post("/api/requests/" + req + "/approve").with(as(owner)), 200);
		call(post("/api/requests/" + req + "/approve").with(as(owner)), 409);
		mvc.perform(get("/api/requests/" + req + "/contact").with(as(adopter))).andExpect(jsonPath("$.whatsapp").value("+584121111111"));
		mvc.perform(get("/api/requests/" + req + "/contact").with(as(owner))).andExpect(jsonPath("$.whatsapp").value("+584142222222"));
		call(get("/api/requests/" + req + "/contact").with(as(other)), 404);
		// HU-09: the other request stays pending
		mvc.perform(get("/api/me/requests").with(as(other))).andExpect(jsonPath("$[0].id").value(otherReq)).andExpect(jsonPath("$[0].status").value("pending"));
		// RNF-04: the public detail never carries WhatsApp
		mvc.perform(get("/api/pets/" + pet)).andExpect(jsonPath("$..whatsapp").isEmpty());
	}

	@Test
	void slowPreEvaluationKeepsTheOwnersDecision() throws Exception {
		var approved = new java.util.concurrent.CountDownLatch(1);
		// the AI "thinks" until the owner has approved, like a slow real call
		when(ai.askJson(eq("pre_evaluation"), any(), any(), any(), any())).thenAnswer(i -> {
			approved.await(5, java.util.concurrent.TimeUnit.SECONDS);
			return Optional.of(json.readTree("{\"summary\":\"Buen hogar.\",\"score\":80}"));
		});
		UUID owner = user("Owner", true, "+584121111111"), adopter = user("Adopter", true, "+584142222222");
		String pet = approvedPet(owner);
		String req = call(withJson(post("/api/pets/" + pet + "/requests"), REQUEST).with(as(adopter)), 201).get("id").asText();
		call(post("/api/requests/" + req + "/approve").with(as(owner)), 200);
		approved.countDown();
		for (int i = 0; i < 50 && jdbc.queryForObject("select ai_score from adoption_requests where id = ?::uuid", Short.class, req) == null; i++) Thread.sleep(100);
		var row = jdbc.queryForMap("select status::text as status, ai_score from adoption_requests where id = ?::uuid", req);
		assertThat(row).containsEntry("status", "approved").containsEntry("ai_score", 80);
	}

	@Test
	void hugeAnswersAreRejected() throws Exception {
		UUID owner = user("Owner", true, "+584121111111"), adopter = user("Adopter", true, "+584142222222");
		String pet = approvedPet(owner);
		String huge = "{\"answers\":{\"x\":\"" + "a".repeat(3000) + "\"},\"motivation\":\"Tengo patio grande y tiempo para pasearlo cada día.\"}";
		mvc.perform(withJson(post("/api/pets/" + pet + "/requests"), huge).with(as(adopter))).andExpect(status().isUnprocessableEntity());
	}

	@Test
	void aiFailureStillCreatesRequest() throws Exception {
		when(ai.askJson(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
		UUID owner = user("Owner", true, "+584121111111"), adopter = user("Adopter", true, "+584142222222");
		String pet = approvedPet(owner);
		call(withJson(post("/api/pets/" + pet + "/requests"), REQUEST).with(as(adopter)), 201);
		Thread.sleep(300);
		mvc.perform(get("/api/pets/" + pet + "/requests").with(as(owner))).andExpect(jsonPath("$[0].aiSummary").isEmpty()).andExpect(jsonPath("$[0].status").value("pending"));
	}

	@Test
	void lifestyleAndMatch() throws Exception {
		UUID owner = user("Owner", true, "+584121111111"), me = user("Me", true, "+584142222222");
		String pet = approvedPet(owner);
		mvc.perform(withJson(post("/api/match"), "{}").with(as(me))).andExpect(jsonPath("$.code").value("LIFESTYLE_REQUIRED"));
		call(withJson(put("/api/me/lifestyle"), "{\"housing\":\"castillo\",\"timeAtHome\":\"mucho\",\"experience\":\"algo\"}").with(as(me)), 422);
		call(withJson(put("/api/me/lifestyle"), "{\"housing\":\"casa\",\"timeAtHome\":\"mucho\",\"kids\":true,\"otherPets\":false,\"experience\":\"algo\"}").with(as(me)), 200);
		mvc.perform(get("/api/me/lifestyle").with(as(me))).andExpect(jsonPath("$.housing").value("casa"));

		when(ai.askJson(eq("match"), any(), any(), any(), any())).thenReturn(Optional.of(json.readTree(
				"{\"matches\":[{\"petId\":\"" + pet + "\",\"score\":91,\"reason\":\"Ideal para casa con patio\"},{\"petId\":\"" + UUID.randomUUID() + "\",\"score\":99,\"reason\":\"inventada\"}]}")));
		mvc.perform(withJson(post("/api/match"), "{\"species\":\"dog\"}").with(as(me)))
				.andExpect(jsonPath("$.aiAvailable").value(true))
				.andExpect(jsonPath("$.matches.length()").value(1))
				.andExpect(jsonPath("$.matches[0].pet.id").value(pet))
				.andExpect(jsonPath("$.matches[0].score").value(91));

		when(ai.askJson(eq("match"), any(), any(), any(), any())).thenReturn(Optional.empty());
		mvc.perform(withJson(post("/api/match"), "{}").with(as(me))).andExpect(jsonPath("$.aiAvailable").value(false));
	}
}
