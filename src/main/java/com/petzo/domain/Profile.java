package com.petzo.domain;

import com.petzo.domain.Types.AccountType;
import com.petzo.domain.Types.Plan;
import com.petzo.domain.Types.Role;
import com.petzo.domain.Types.VerificationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/** Rows are created by the auth.users trigger (V2); the API only updates them. */
@Entity
@Table(name = "profiles")
public class Profile {

	@Id public UUID id;
	public String fullName;
	public String avatarUrl;
	public String whatsapp;
	public Short municipalityId;

	@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(columnDefinition = "user_role")
	public Role role;

	@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(columnDefinition = "account_type")
	public AccountType accountType;

	public String foundationName;
	public String foundationInstagram;
	public String foundationBio;

	@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(columnDefinition = "verification_status")
	public VerificationStatus verificationStatus;

	@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(columnDefinition = "plan_type")
	public Plan plan;

	@Column(name = "is_suspended") public boolean suspended;

	@Generated(event = EventType.INSERT) @Column(insertable = false, updatable = false) public Instant createdAt;
	@Generated(event = {EventType.INSERT, EventType.UPDATE}) @Column(insertable = false, updatable = false) public Instant updatedAt;

	public boolean isComplete() { return whatsapp != null && municipalityId != null; }

	public boolean isVerifiedFoundation() {
		return accountType == AccountType.foundation && verificationStatus == VerificationStatus.verified;
	}
}
