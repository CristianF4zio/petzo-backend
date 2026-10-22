package com.petzo.domain;

import com.petzo.domain.Types.PetSource;
import com.petzo.domain.Types.PetStatus;
import com.petzo.domain.Types.Sex;
import com.petzo.domain.Types.Size;
import com.petzo.domain.Types.Species;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pets")
public class Pet {

	@Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;

	// EAGER: entities use public fields, which lazy proxies do not intercept; the owner is always shown anyway
	@ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "owner_id")
	public Profile owner;

	@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(columnDefinition = "pet_species")
	public Species species;

	public String name;
	public Short ageMonths;

	@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(columnDefinition = "pet_sex")
	public Sex sex;

	@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(columnDefinition = "pet_size")
	public Size size;

	public Short municipalityId;
	public boolean vaccinated;
	public boolean sterilized;
	public boolean dewormed;
	public String description;

	@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(columnDefinition = "pet_status")
	public PetStatus status;

	public String moderationReason;
	public Short riskScore;

	@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(columnDefinition = "pet_source")
	public PetSource source = PetSource.app;

	public String sourceUrl;

	@OneToMany(mappedBy = "pet", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("position")
	public List<PetPhoto> photos = new ArrayList<>();

	@Generated(event = EventType.INSERT) @Column(insertable = false, updatable = false) public Instant createdAt;
	@Generated(event = {EventType.INSERT, EventType.UPDATE}) @Column(insertable = false, updatable = false) public Instant updatedAt;

	public boolean isActive() { return status == PetStatus.pending_review || status == PetStatus.approved; }
}
