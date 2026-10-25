package com.petzo.web;

import com.petzo.domain.Types.*;
import com.petzo.service.*;
import com.petzo.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Public catalog, the signed-in user and their pets. */
@RestController
@RequestMapping("/api")
public class ApiController {

	private final CurrentUser current;
	private final PetService pets;
	private final ProfileService profiles;
	private final EngagementService engagement;

	public ApiController(CurrentUser current, PetService pets, ProfileService profiles, EngagementService engagement) {
		this.current = current;
		this.pets = pets;
		this.profiles = profiles;
		this.engagement = engagement;
	}

	// ---- public ----

	@GetMapping("/municipalities")
	List<MunicipalityDto> municipalities() { return profiles.municipalities(); }

	/** size accepts one value or a comma list (small,medium). */
	@GetMapping("/pets")
	Page<PetDto> catalog(@RequestParam(required = false) Species species, @RequestParam(required = false) String size,
			@RequestParam(required = false) Sex sex, @RequestParam(required = false) Short ageMin, @RequestParam(required = false) Short ageMax,
			@RequestParam(required = false) Short municipalityId, @RequestParam(defaultValue = "1") int page) {
		List<Size> sizes;
		try {
			sizes = size == null || size.isBlank() ? List.of() : Arrays.stream(size.split(",")).map(Size::valueOf).toList();
		} catch (IllegalArgumentException e) {
			throw new com.petzo.common.ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Tamaño inválido");
		}
		return pets.catalog(species, sizes, sex, ageMin, ageMax, municipalityId, page);
	}

	@GetMapping("/pets/{id}")
	PetDto detail(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { return pets.detail(id, current.idOrNull(jwt)); }

	// ---- me ----

	@GetMapping("/me")
	ProfileDto me(@AuthenticationPrincipal Jwt jwt) { return profiles.me(current.require(jwt)); }

	@PutMapping("/me")
	ProfileDto updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateMe in) { return profiles.update(current.require(jwt), in); }

	@PostMapping(path = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ProfileDto uploadAvatar(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) { return profiles.uploadAvatar(current.require(jwt), file); }

	@PostMapping("/me/foundation-request")
	ProfileDto foundationRequest(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FoundationRequest in) {
		return profiles.requestFoundation(current.require(jwt), in);
	}

	@GetMapping("/me/pets")
	List<PetDto> myPets(@AuthenticationPrincipal Jwt jwt) { return pets.mine(current.require(jwt)); }

	@GetMapping("/me/favorites")
	List<PetDto> myFavorites(@AuthenticationPrincipal Jwt jwt) { return engagement.favorites(current.require(jwt)); }

	// ---- pets ----

	@PostMapping("/pets")
	@ResponseStatus(HttpStatus.CREATED)
	PetDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PetInput in) { return pets.create(current.require(jwt), in); }

	@PutMapping("/pets/{id}")
	PetDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody PetInput in) { return pets.update(current.require(jwt), id, in); }

	@DeleteMapping("/pets/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteDraft(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { pets.deleteDraft(current.require(jwt), id); }

	@PatchMapping("/pets/{id}/status")
	PetDto status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody StatusInput in) {
		return pets.changeStatus(current.require(jwt), id, in.status());
	}

	@PostMapping(path = "/pets/{id}/photos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	PhotoPath uploadPhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
		return pets.uploadPhoto(current.require(jwt), id, file);
	}

	/** Whole gallery in order (first = cover); the app uses this instead of single add/delete. */
	@PutMapping("/pets/{id}/photos")
	PetDto replacePhotos(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody PhotosInput in) {
		return pets.replacePhotos(current.require(jwt), id, in.paths());
	}

	@PostMapping("/pets/{id}/reports")
	@ResponseStatus(HttpStatus.CREATED)
	void report(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ReportInput in) {
		engagement.report(current.require(jwt), id, in.reason());
	}

	@PostMapping("/favorites/{petId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void addFavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId) { engagement.addFavorite(current.require(jwt), petId); }

	@DeleteMapping("/favorites/{petId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeFavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId) { engagement.removeFavorite(current.require(jwt), petId); }
}
