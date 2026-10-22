package com.petzo.service;

import com.petzo.domain.Pet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Outbound hooks to n8n (flow 1 moderation, flow 2 notifications). Wired to webhooks in B3. */
@Component
public class Events {

	private static final Logger log = LoggerFactory.getLogger(Events.class);

	public void moderate(Pet pet) {
		log.info("moderation requested for pet {}", pet.id);
	}

	public void petStatusChanged(Pet pet) {
		log.info("pet {} is now {}", pet.id, pet.status);
	}

	public void requestCreated(com.petzo.domain.AdoptionRequest r) {
		log.info("request {} created for pet {}", r.id, r.pet.id);
	}

	public void requestStatusChanged(com.petzo.domain.AdoptionRequest r) {
		log.info("request {} is now {}", r.id, r.status);
	}
}
