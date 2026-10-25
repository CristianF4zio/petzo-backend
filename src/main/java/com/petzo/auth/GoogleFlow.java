package com.petzo.auth;

import com.petzo.auth.GoTrue.Session;
import com.petzo.common.ApiException;
import com.petzo.config.PetzoProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Google sign-in coordinated by the API (PKCE against Supabase). The client sends only a random flow id and
 * sha256(secret); tokens are handed over once, to whoever proves the secret, in a POST body — never in a URL.
 */
@Component
public class GoogleFlow {

	private static final Duration TTL = Duration.ofMinutes(5);
	private static final SecureRandom RANDOM = new SecureRandom();

	private record Pending(String challenge, String redirect, String verifier, Instant createdAt, Session session) {}

	// shortcut: in-memory, so it works on one API instance only; move to a table if the API ever scales out
	private final Map<String, Pending> flows = new ConcurrentHashMap<>();
	private final GoTrue gotrue;
	private final PetzoProperties props;

	public GoogleFlow(GoTrue gotrue, PetzoProperties props) {
		this.gotrue = gotrue;
		this.props = props;
	}

	/** Returns the Supabase authorize URL; {@code callbackUrl} is this API's own callback for this flow. */
	public String start(String flow, String challenge, String redirect, String callbackUrl) {
		if (!flow.matches("[0-9a-fA-F-]{36}") || !challenge.matches("[A-Za-z0-9_-]{43}")) throw invalid("Solicitud de inicio de sesión inválida");
		// Only known return targets: otherwise this endpoint would be an open redirect
		if (props.authRedirectAllowlist().stream().noneMatch(redirect::startsWith)) throw invalid("Destino de regreso no permitido");
		flows.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(Instant.now().minus(TTL)));
		String verifier = random();
		if (flows.putIfAbsent(flow, new Pending(challenge, redirect, verifier, Instant.now(), null)) != null) throw invalid("Ese inicio de sesión ya existe");
		return gotrue.authorizeUrl(callbackUrl, sha256(verifier));
	}

	/** Exchanges Supabase's code and returns where to send the browser back (with ?error= on failure, never tokens). */
	public String callback(String flow, String code, String error) {
		Pending p = flows.get(flow);
		if (p == null) return null;
		if (code == null || error != null) {
			flows.remove(flow);
			return p.redirect() + "?error=" + (error == null ? "cancelled" : "google");
		}
		try {
			flows.put(flow, new Pending(p.challenge(), p.redirect(), null, p.createdAt(), gotrue.exchangeCode(code, p.verifier())));
			return p.redirect();
		} catch (ApiException e) {
			flows.remove(flow);
			return p.redirect() + "?error=exchange";
		}
	}

	public Session claim(String flow, String secret) {
		Pending p = flows.get(flow);
		boolean ok = p != null && p.session() != null && p.createdAt().isAfter(Instant.now().minus(TTL))
				&& MessageDigest.isEqual(sha256(secret).getBytes(StandardCharsets.US_ASCII), p.challenge().getBytes(StandardCharsets.US_ASCII));
		if (!ok) throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "El inicio de sesión con Google expiró; intenta de nuevo");
		flows.remove(flow); // one-time
		return p.session();
	}

	static String sha256(String value) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String random() {
		byte[] b = new byte[32];
		RANDOM.nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	private static ApiException invalid(String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message);
	}
}
