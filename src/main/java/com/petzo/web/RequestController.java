package com.petzo.web;

import com.petzo.service.CurrentUser;
import com.petzo.service.MatchService;
import com.petzo.service.RequestService;
import com.petzo.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Adoption requests, lifestyle questionnaire and AI match (B2). */
@RestController
@RequestMapping("/api")
public class RequestController {

	private final CurrentUser current;
	private final RequestService requests;
	private final MatchService match;

	public RequestController(CurrentUser current, RequestService requests, MatchService match) {
		this.current = current;
		this.requests = requests;
		this.match = match;
	}

	@PostMapping("/pets/{id}/requests")
	@ResponseStatus(HttpStatus.CREATED)
	RequestDto create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody RequestInput in) {
		return requests.create(current.require(jwt), id, in);
	}

	@GetMapping("/pets/{id}/requests")
	List<RequestDto> forPet(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return requests.forPet(current.require(jwt), id); }

	@GetMapping("/me/requests")
	List<MyRequestDto> mine(@AuthenticationPrincipal Jwt jwt) { return requests.mine(current.require(jwt)); }

	@PostMapping("/requests/{id}/approve")
	RequestDto approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return requests.decide(current.require(jwt), id, true); }

	@PostMapping("/requests/{id}/reject")
	RequestDto reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return requests.decide(current.require(jwt), id, false); }

	@GetMapping("/requests/{id}/contact")
	ContactDto contact(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return requests.contact(current.require(jwt), id); }

	@GetMapping("/me/lifestyle")
	Map<String, Object> lifestyle(@AuthenticationPrincipal Jwt jwt) { return match.getLifestyle(current.require(jwt)); }

	@PutMapping("/me/lifestyle")
	Map<String, Object> saveLifestyle(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Lifestyle in) { return match.saveLifestyle(current.require(jwt), in); }

	@PostMapping("/match")
	MatchResult match(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) MatchInput in) {
		return match.match(current.require(jwt), in == null ? new MatchInput(null, null, null) : in);
	}
}
