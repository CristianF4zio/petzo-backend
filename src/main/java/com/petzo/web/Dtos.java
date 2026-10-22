package com.petzo.web;

import com.petzo.domain.Types;
import com.petzo.domain.Types.*;
import jakarta.validation.constraints.*;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response records. JSON is camelCase; enum values are the DB labels. */
public final class Dtos {

	private Dtos() {}

	public record MunicipalityDto(short id, String name) {}

	public record ProfileDto(UUID id, String fullName, String avatarUrl, String whatsapp, Short municipalityId, Role role,
			AccountType accountType, String foundationName, String foundationInstagram, String foundationBio,
			VerificationStatus verificationStatus, Plan plan, boolean complete) {}

	public record UpdateMe(@NotBlank @Size(min = 2, max = 80) String fullName, @NotBlank String whatsapp, @NotNull Short municipalityId) {}

	public record FoundationRequest(@NotBlank @Size(min = 2, max = 80) String foundationName,
			@NotBlank @Pattern(regexp = "@?[A-Za-z0-9._]{1,30}", message = "Usuario de Instagram inválido") String foundationInstagram,
			@Size(max = 500) String foundationBio) {}

	public record PublisherDto(UUID id, String name, String avatarUrl, Short municipalityId, boolean foundation, boolean verified, Long petsCount) {}

	public record PhotoDto(UUID id, String url) {}

	/** moderationReason/riskScore are filled only for the owner or an admin. */
	public record PetDto(UUID id, Species species, String name, short ageMonths, Sex sex, Types.Size size, Short municipalityId,
			boolean vaccinated, boolean sterilized, boolean dewormed, String description, PetStatus status, PetSource source,
			List<PhotoDto> photos, PublisherDto publisher, String moderationReason, Short riskScore, Instant createdAt, Instant updatedAt) {}

	public record PetInput(@NotNull Species species, @NotBlank @Size(max = 60) String name, @NotNull @Min(0) @Max(360) Short ageMonths,
			@NotNull Sex sex, @NotNull Types.Size size, @NotNull Short municipalityId, boolean vaccinated, boolean sterilized, boolean dewormed,
			@Size(max = 1000) String description) {}

	public record StatusInput(@NotNull PetStatus status) {}

	public record PhotoInput(@NotBlank String path) {}

	public record UploadUrl(String path, String signedUrl, String token) {}

	public record ReportInput(@NotBlank @Size(min = 3, max = 500) String reason) {}

	public record Page<T>(List<T> items, int page, int pageSize, long total) {}

	// Requests + match (B2)
	public record RequestInput(@NotNull java.util.Map<String, Object> answers, @NotBlank @Size(min = 20, max = 500) String motivation) {}

	public record AdopterDto(UUID id, String name, String avatarUrl, Short municipalityId) {}

	public record RequestDto(UUID id, UUID petId, RequestStatus status, AdopterDto adopter, java.util.Map<String, Object> answers, String motivation,
			String aiSummary, Short aiScore, Instant createdAt, Instant decidedAt) {}

	public record MyRequestDto(UUID id, RequestStatus status, UUID petId, String petName, String petPhoto, Instant createdAt, Instant decidedAt) {}

	public record ContactDto(String name, String whatsapp) {}

	public record Lifestyle(@NotBlank @Pattern(regexp = "casa|apartamento|otro") String housing, @NotBlank @Pattern(regexp = "poco|medio|mucho") String timeAtHome,
			boolean kids, boolean otherPets, @NotBlank @Pattern(regexp = "ninguna|algo|mucha") String experience) {}

	public record MatchInput(Species species, Types.Size size, Short municipalityId) {}

	public record MatchDto(PetDto pet, int score, String reason) {}

	public record MatchResult(List<MatchDto> matches, boolean aiAvailable) {}

	// Admin
	public record RejectInput(@NotBlank @Size(max = 500) String reason) {}

	public record PlanInput(@NotNull Plan plan) {}

	public record SuspendInput(boolean suspended) {}

	public record ReportDto(UUID id, UUID petId, String petName, String reason, Instant createdAt) {}
}
