package com.petzo.config;

import com.petzo.domain.Repos.ProfileRepo;
import com.petzo.domain.Types.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain api(HttpSecurity http, ProfileRepo profiles, PetzoProperties props) throws Exception {
		return http
				.csrf(c -> c.disable())
				.cors(c -> {})
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(a -> a
						.requestMatchers(HttpMethod.GET, "/api/pets", "/api/pets/*", "/api/municipalities").permitAll()
						.requestMatchers("/internal/**", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						.requestMatchers("/api/admin/**").access((auth, ctx) -> new AuthorizationDecision(
								auth.get().getPrincipal() instanceof Jwt jwt
										&& profiles.findById(UUID.fromString(jwt.getSubject())).map(p -> p.role == Role.admin && !p.suspended).orElse(false)))
						.anyRequest().authenticated())
				.oauth2ResourceServer(o -> o.jwt(j -> {}))
				.addFilterBefore(new InternalSecretFilter(props.internalSecret()), org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
				.build();
	}

	// Only signed-in users: rejects anon keys and anonymous sign-ins
	@Bean
	JwtDecoder jwtDecoder(PetzoProperties props) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.supabaseUrl() + "/auth/v1/.well-known/jwks.json")
				.jwsAlgorithms(a -> { a.add(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES256); a.add(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256); })
				.build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), jwt ->
				"authenticated".equals(jwt.getClaimAsString("role")) && !Boolean.TRUE.equals(jwt.getClaimAsBoolean("is_anonymous"))
						? OAuth2TokenValidatorResult.success()
						: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not a signed-in user", null))));
		return decoder;
	}

	/** /internal/** is for n8n and the Stagehand worker: shared secret, constant-time compare. */
	static class InternalSecretFilter extends OncePerRequestFilter {
		private final byte[] secret;

		InternalSecretFilter(String secret) { this.secret = secret.getBytes(StandardCharsets.UTF_8); }

		@Override
		protected boolean shouldNotFilter(HttpServletRequest req) { return !req.getRequestURI().startsWith("/internal/"); }

		@Override
		protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws java.io.IOException, jakarta.servlet.ServletException {
			String given = req.getHeader("x-internal-secret");
			if (secret.length == 0 || given == null || !MessageDigest.isEqual(secret, given.getBytes(StandardCharsets.UTF_8))) {
				res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
			chain.doFilter(req, res);
		}
	}
}
