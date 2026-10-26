package com.petzo.web;

import com.petzo.service.AdminService;
import com.petzo.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Admin panel API. Access is checked in SecurityConfig (profiles.role = 'admin'). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final AdminService admin;
	private final com.petzo.service.InternalService imports;
	private final com.petzo.service.CurrentUser current;

	public AdminController(AdminService admin, com.petzo.service.InternalService imports, com.petzo.service.CurrentUser current) {
		this.admin = admin;
		this.imports = imports;
		this.current = current;
	}

	@PostMapping("/imports")
	@ResponseStatus(HttpStatus.CREATED)
	com.petzo.domain.Simple.ImportJob createImport(@org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt,
			@Valid @RequestBody ImportInput in) {
		return imports.createImport(current.require(jwt), in.instagramHandle());
	}

	@GetMapping("/imports")
	List<com.petzo.domain.Simple.ImportJob> imports() { return imports.imports(); }

	@GetMapping("/imported-foundations")
	List<com.petzo.web.Dtos.ImportedFoundation> importedFoundations() { return imports.importedFoundations(); }

	@GetMapping("/moderation")
	List<PetDto> moderation() { return admin.moderationQueue(); }

	@PostMapping("/pets/{id}/approve")
	PetDto approve(@PathVariable UUID id, @RequestParam(required = false) Integer version) { return admin.approve(id, version); }

	@PostMapping("/pets/{id}/reject")
	PetDto reject(@PathVariable UUID id, @Valid @RequestBody RejectInput in) { return admin.reject(id, in.reason()); }

	@GetMapping("/foundations")
	List<ProfileDto> foundations() { return admin.foundations(); }

	@PostMapping("/foundations/{userId}/verify")
	ProfileDto verify(@PathVariable UUID userId) { return admin.verify(userId, true); }

	@PostMapping("/foundations/{userId}/reject")
	ProfileDto rejectFoundation(@PathVariable UUID userId) { return admin.verify(userId, false); }

	@GetMapping("/users")
	List<ProfileDto> users() { return admin.users(); }

	@PutMapping("/users/{id}/plan")
	ProfileDto plan(@PathVariable UUID id, @Valid @RequestBody PlanInput in) { return admin.setPlan(id, in.plan()); }

	@PutMapping("/users/{id}/suspend")
	ProfileDto suspend(@PathVariable UUID id, @RequestBody SuspendInput in) { return admin.suspend(id, in.suspended()); }

	@GetMapping("/reports")
	List<ReportDto> reports() { return admin.openReports(); }

	@PostMapping("/reports/{id}/resolve")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resolve(@PathVariable UUID id) { admin.resolveReport(id); }

	@GetMapping("/stats")
	Map<String, Object> stats() { return admin.stats(); }
}
