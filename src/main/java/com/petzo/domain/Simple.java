package com.petzo.domain;

import com.petzo.domain.Types.ReportStatus;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/** Small tables with no behaviour, kept together. */
public final class Simple {

	private Simple() {}

	@Entity @Table(name = "municipalities")
	public static class Municipality {
		@Id public Short id;
		public String name;
		public String state;
	}

	public record FavoriteId(UUID userId, UUID petId) implements Serializable {
		public FavoriteId() { this(null, null); }
	}

	@Entity @Table(name = "favorites") @IdClass(FavoriteId.class)
	public static class Favorite {
		@Id public UUID userId;
		@Id public UUID petId;
		@Generated(event = EventType.INSERT) @Column(insertable = false, updatable = false) public Instant createdAt;
	}

	@Entity @Table(name = "lifestyle_profiles")
	public static class LifestyleProfile {
		@Id public UUID userId;
		@org.hibernate.annotations.JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") public java.util.Map<String, Object> answers;
		@Generated(event = EventType.INSERT) @Column(insertable = false, updatable = false) public Instant createdAt;
	}

	@Entity @Table(name = "import_jobs")
	public static class ImportJob {
		@Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;
		public String instagramHandle;
		public UUID requestedBy;
		@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(columnDefinition = "import_status")
		public Types.ImportStatus status = Types.ImportStatus.queued;
		public int petsCreated;
		public String error;
		public String foundationName;
		public String sourceUrl;
		@Generated(event = EventType.INSERT) @Column(insertable = false, updatable = false) public Instant createdAt;
	}

	@Entity @Table(name = "reports")
	public static class Report {
		@Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;
		public UUID petId;
		public UUID reporterId;
		public String reason;
		@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(columnDefinition = "report_status")
		public ReportStatus status = ReportStatus.open;
		@Generated(event = EventType.INSERT) @Column(insertable = false, updatable = false) public Instant createdAt;
	}
}
