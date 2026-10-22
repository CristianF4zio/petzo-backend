package com.petzo.domain;

/**
 * Postgres enums (SPEC §14). Constants match the DB labels exactly, so they are stored and
 * serialized as-is (e.g. "pending_review") without converters.
 */
public final class Types {

	private Types() {}

	public enum Role { user, admin }
	public enum AccountType { person, foundation }
	public enum VerificationStatus { none, pending, verified, rejected }
	public enum Plan { free, pro }
	public enum Species { dog, cat }
	public enum Sex { male, female }
	public enum Size { small, medium, large }
	public enum PetStatus { draft, pending_review, approved, rejected, paused, adopted }
	public enum PetSource { app, instagram }
	public enum RequestStatus { pending, approved, rejected, withdrawn }
	public enum ReportStatus { open, resolved }
	public enum AiTask { moderation, pre_evaluation, match, import_ }
	public enum ImportStatus { queued, running, done, failed }
}
