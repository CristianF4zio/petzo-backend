package com.petzo;

import static com.petzo.ApiTest.withJson;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.petzo.auth.GoTrue;
import com.petzo.auth.GoTrue.Session;
import com.petzo.storage.StorageClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthTest {

	@Autowired MockMvc mvc;
	@MockitoBean GoTrue gotrue;
	@MockitoBean StorageClient storage;

	static String challenge(String secret) throws Exception {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.US_ASCII)));
	}

	@Test
	void emailLoginGoesThroughTheApi() throws Exception {
		when(gotrue.password("ana@test.dev", "secreta123")).thenReturn(new Session("access", "refresh", 3600));
		mvc.perform(withJson(post("/api/auth/login"), "{\"email\":\"ana@test.dev\",\"password\":\"secreta123\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("access")).andExpect(jsonPath("$.refreshToken").value("refresh"));
		mvc.perform(withJson(post("/api/auth/register"), "{\"email\":\"no-es-correo\",\"password\":\"corta\",\"fullName\":\"\"}")).andExpect(status().isUnprocessableEntity());
	}

	@Test
	void googleFlowNeverPutsTokensInUrlsAndIsOneTime() throws Exception {
		when(gotrue.authorizeUrl(anyString(), anyString())).thenReturn("https://supabase.test/auth/v1/authorize?provider=google");
		when(gotrue.exchangeCode(eq("the-code"), anyString())).thenReturn(new Session("access", "refresh", 3600));
		String flow = UUID.randomUUID().toString(), secret = "app-only-secret-" + UUID.randomUUID();
		String start = "/api/auth/google/start?flow=" + flow + "&challenge=" + challenge(secret) + "&redirect=";

		mvc.perform(get(start + "https://evil.test/steal")).andExpect(status().isUnprocessableEntity()); // no open redirect
		mvc.perform(get(start + "http://localhost:5173@evil.test/")).andExpect(status().isUnprocessableEntity()); // prefix must end at a boundary
		mvc.perform(get(start + "exp://192.168.0.8:8081/--/auth-callback")).andExpect(status().isFound())
				.andExpect(header().string("Location", "https://supabase.test/auth/v1/authorize?provider=google"));
		// Supabase comes back with a code; the browser goes back to the app with no token in the URL
		mvc.perform(get("/api/auth/google/callback?flow=" + flow + "&code=the-code")).andExpect(status().isFound())
				.andExpect(header().string("Location", "exp://192.168.0.8:8081/--/auth-callback"));
		// a reload of the callback goes back again instead of a 500 (the code was already exchanged)
		mvc.perform(get("/api/auth/google/callback?flow=" + flow + "&code=the-code")).andExpect(status().isFound())
				.andExpect(header().string("Location", "exp://192.168.0.8:8081/--/auth-callback"));

		String claim = "{\"flow\":\"" + flow + "\",\"secret\":\"%s\"}";
		mvc.perform(withJson(post("/api/auth/google/claim"), claim.formatted("wrong"))).andExpect(status().isUnauthorized());
		mvc.perform(withJson(post("/api/auth/google/claim"), claim.formatted(secret))).andExpect(jsonPath("$.accessToken").value("access"));
		mvc.perform(withJson(post("/api/auth/google/claim"), claim.formatted(secret))).andExpect(status().isUnauthorized()); // one-time
	}

	@Test
	void photosAreServedByTheApiOnlyFromOurFolders() throws Exception {
		String path = "pets/" + UUID.randomUUID() + "/" + UUID.randomUUID() + ".jpg";
		when(storage.download(path)).thenReturn(Optional.of(ResponseEntity.ok(new byte[] { (byte) 0xFF, (byte) 0xD8 })));
		mvc.perform(get("/api/photos/" + path)).andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/jpeg"));
		mvc.perform(get("/api/photos/../secrets.txt")).andExpect(status().is4xxClientError());
		mvc.perform(get("/api/photos/other-bucket/x.jpg")).andExpect(status().isNotFound());
	}
}
