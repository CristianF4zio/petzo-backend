package com.petzo.domain;

import com.petzo.domain.Types.RequestStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "adoption_requests")
public class AdoptionRequest {

	@Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;

	// EAGER: public fields are not intercepted by lazy proxies
	@ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "pet_id") public Pet pet;
	@ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "adopter_id") public Profile adopter;

	@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(columnDefinition = "request_status")
	public RequestStatus status = RequestStatus.pending;

	@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") public Map<String, Object> answers;
	public String motivation;
	public String aiSummary;
	public Short aiScore;
	public Instant decidedAt;

	@Generated(event = EventType.INSERT) @Column(insertable = false, updatable = false) public Instant createdAt;
	@Generated(event = {EventType.INSERT, EventType.UPDATE}) @Column(insertable = false, updatable = false) public Instant updatedAt;
}
