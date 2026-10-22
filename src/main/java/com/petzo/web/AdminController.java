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

	public AdminController(AdminService admin) { this.admin = admin; }

	@GetMapping("/moderation")
	List<PetDto> moderation() { return admin.moderationQueue(); }

	@PostMapping("/pets/{id}/approve")
	PetDto approve(@PathVariable UUID id) { return admin.approve(id); }

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
