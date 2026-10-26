package com.petzo.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.petzo.common.ApiException;
import com.petzo.config.PetzoProperties;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/** Supabase Auth (GoTrue) REST, called only from the API: the app and the panel never talk to Supabase. */
@Component
public class GoTrue {

	public record Session(String accessToken, String refreshToken, long expiresIn) {}

	private final RestClient http;
	private final String base;

	public GoTrue(PetzoProperties props) {
		this.base = props.supabaseUrl() + "/auth/v1";
		this.http = RestClient.builder().baseUrl(base).requestFactory(com.petzo.config.WebConfig.timeouts(java.time.Duration.ofSeconds(20)))
				.defaultHeader("apikey", props.supabaseAnonKey()).build();
	}

	public Session signUp(String email, String password, String fullName) {
		JsonNode res = call(() -> http.post().uri("/signup").body(Map.of("email", email, "password", password, "data", Map.of("full_name", fullName)))
				.retrieve().body(JsonNode.class));
		// Email confirmation on in Supabase: a user comes back without a session
		if (!res.hasNonNull("access_token")) throw ApiException.conflict("CONFIRM_EMAIL", "Te enviamos un correo para confirmar tu cuenta");
		return session(res);
	}

	public Session password(String email, String password) {
		return session(call(() -> http.post().uri("/token?grant_type=password").body(Map.of("email", email, "password", password)).retrieve().body(JsonNode.class)));
	}

	public Session refresh(String refreshToken) {
		return session(call(() -> http.post().uri("/token?grant_type=refresh_token").body(Map.of("refresh_token", refreshToken)).retrieve().body(JsonNode.class)));
	}

	public Session exchangeCode(String code, String verifier) {
		return session(call(() -> http.post().uri("/token?grant_type=pkce").body(Map.of("auth_code", code, "code_verifier", verifier)).retrieve().body(JsonNode.class)));
	}

	public void logout(String accessToken) {
		try {
			http.post().uri("/logout").header("Authorization", "Bearer " + accessToken).retrieve().toBodilessEntity();
		} catch (RestClientResponseException ignored) {
			// already expired or revoked: the client drops its tokens anyway
		}
	}

	/** Supabase redirects to Google's own consent screen and then back to {@code redirectTo} with {@code ?code=}. */
	public String authorizeUrl(String redirectTo, String challenge) {
		return UriComponentsBuilder.fromUriString(base + "/authorize").queryParam("provider", "google").queryParam("redirect_to", redirectTo)
				.queryParam("code_challenge", challenge).queryParam("code_challenge_method", "s256").encode().toUriString();
	}

	private static Session session(JsonNode res) {
		return new Session(res.path("access_token").asText(), res.path("refresh_token").asText(), res.path("expires_in").asLong(3600));
	}

	// GoTrue error codes → our ProblemDetail codes; never echo its body (it can contain the email)
	private static JsonNode call(java.util.function.Supplier<JsonNode> request) {
		try {
			return request.get();
		} catch (RestClientResponseException e) {
			String body = e.getResponseBodyAsString();
			if (body.contains("invalid_credentials") || body.contains("invalid_grant")) {
				throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Correo o contraseña incorrectos");
			}
			if (body.contains("user_already_exists") || body.contains("email_exists")) {
				throw ApiException.conflict("EMAIL_TAKEN", "Ya existe una cuenta con ese correo");
			}
			if (body.contains("email_not_confirmed")) throw new ApiException(HttpStatus.FORBIDDEN, "CONFIRM_EMAIL", "Confirma tu correo antes de entrar");
			if (body.contains("over_") && body.contains("rate_limit")) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Demasiados intentos; espera un momento");
			if (body.contains("weak_password")) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "La contraseña es muy débil");
			if (body.contains("refresh_token") || body.contains("flow_state")) throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "La sesión expiró; vuelve a entrar");
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AUTH_UNAVAILABLE", "No pudimos conectar con el servicio de autenticación");
		}
	}
}
