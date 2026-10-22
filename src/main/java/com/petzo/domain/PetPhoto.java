package com.petzo.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "pet_photos")
public class PetPhoto {

	@Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "pet_id")
	public Pet pet;

	public String storagePath;
	public short position;
}
