package com.petzo.auth;

import com.petzo.auth.GoTrue.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Email/password and Google sign-in for the app and the admin panel; only the API talks to Supabase Auth (RF-01, RF-02). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	public record Register(@NotBlank @Email String email, @NotBlank @Size(min = 8, max = 72) String password, @NotBlank @Size(max = 80) String fullName) {}

	public record Login(@NotBlank @Email String email, @NotBlank String password) {}

	public record Refresh(@NotBlank String refreshToken) {}

	public record Claim(@NotBlank String flow, @NotBlank String secret) {}

	private final GoTrue gotrue;
	private final GoogleFlow google;

	public AuthController(GoTrue gotrue, GoogleFlow google) {
		this.gotrue = gotrue;
		this.google = google;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	Session register(@Valid @RequestBody Register in) { return gotrue.signUp(in.email().trim(), in.password(), in.fullName().trim()); }

	@PostMapping("/login")
	Session login(@Valid @RequestBody Login in) { return gotrue.password(in.email().trim(), in.password()); }

	@PostMapping("/refresh")
	Session refresh(@Valid @RequestBody Refresh in) { return gotrue.refresh(in.refreshToken()); }

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(@AuthenticationPrincipal Jwt jwt) { gotrue.logout(jwt.getTokenValue()); }

	/** Opens Google's own consent screen through Supabase; the browser comes back to /callback on this API. */
	@GetMapping("/google/start")
	ResponseEntity<Void> googleStart(@RequestParam String flow, @RequestParam String challenge, @RequestParam String redirect) {
		// Built from the host the client used (LAN IP for the phone, localhost for the panel); it must be in Supabase's redirect allow list
		String callback = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/auth/google/callback").queryParam("flow", flow).toUriString();
		return found(google.start(flow, challenge, redirect, callback));
	}

	@GetMapping("/google/callback")
	ResponseEntity<Void> googleCallback(@RequestParam String flow, @RequestParam(required = false) String code, @RequestParam(required = false) String error) {
		String back = google.callback(flow, code, error);
		return back == null ? ResponseEntity.notFound().build() : found(back);
	}

	@PostMapping("/google/claim")
	Session googleClaim(@Valid @RequestBody Claim in) { return google.claim(in.flow(), in.secret()); }

	private static ResponseEntity<Void> found(String location) {
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
	}
}
