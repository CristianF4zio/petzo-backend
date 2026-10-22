package com.petzo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("petzo")
public record PetzoProperties(
		String supabaseUrl,
		String serviceRoleKey,
		String internalSecret,
		String anthropicApiKey,
		String aiModel,
		long aiDailyTokenCap,
		String n8nModerationUrl,
		String n8nNotifyUrl) {}
