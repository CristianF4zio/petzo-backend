package com.petzo.web;

import com.petzo.service.InternalService;
import com.petzo.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** For n8n and the Stagehand worker. Guarded by x-internal-secret (constant-time check in SecurityConfig). */
@RestController
@RequestMapping("/internal")
public class InternalController {

	private final InternalService internal;

	public InternalController(InternalService internal) { this.internal = internal; }

	@PostMapping("/pets/{id}/moderation-result")
	Map<String, String> moderation(@PathVariable UUID id, @Valid @RequestBody ModerationResult in) {
		return Map.of("status", internal.moderationResult(id, in));
	}

	@GetMapping("/imports/next")
	ResponseEntity<Map<String, Object>> next() {
		return internal.claimNext().map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
	}

	@PostMapping("/imports/{id}/pets")
	Map<String, Integer> pets(@PathVariable UUID id, @Valid @RequestBody ImportedPets in) {
		return Map.of("created", internal.addImportedPets(id, in));
	}

	@PostMapping("/imports/{id}/status")
	ResponseEntity<Void> status(@PathVariable UUID id, @Valid @RequestBody ImportStatusInput in) {
		internal.finishImport(id, in);
		return ResponseEntity.noContent().build();
	}
}
