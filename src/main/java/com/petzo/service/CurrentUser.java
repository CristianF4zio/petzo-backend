package com.petzo.service;

import com.petzo.common.ApiException;
import com.petzo.domain.Profile;
import com.petzo.domain.Repos.ProfileRepo;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

	private final ProfileRepo profiles;

	public CurrentUser(ProfileRepo profiles) { this.profiles = profiles; }

	/** Signed-in, existing and not suspended. The profile row comes from the auth trigger. */
	public Profile require(Jwt jwt) {
		Profile p = profiles.findById(UUID.fromString(jwt.getSubject()))
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Sesión inválida o expirada"));
		if (p.suspended) throw new ApiException(HttpStatus.FORBIDDEN, "SUSPENDED", "Tu cuenta está suspendida");
		return p;
	}

	public UUID idOrNull(Jwt jwt) { return jwt == null ? null : UUID.fromString(jwt.getSubject()); }

	public static void requireComplete(Profile p) {
		if (!p.isComplete()) throw ApiException.conflict("PROFILE_INCOMPLETE", "Completa tu zona y WhatsApp para continuar");
	}
}
