package com.petzo.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("petzo")
public record PetzoProperties(
		String supabaseUrl,
		String serviceRoleKey,
		String supabaseAnonKey,
		String internalSecret,
		String openrouterApiKey,
		String aiModel,
		long aiDailyTokenCap,
		String n8nModerationUrl,
		String n8nNotifyUrl,
		List<String> authRedirectAllowlist,
		List<String> importAllowedHosts) {}
