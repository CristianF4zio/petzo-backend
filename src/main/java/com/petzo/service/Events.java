package com.petzo.service;

import com.petzo.ai.AiService;
import com.petzo.config.PetzoProperties;
import com.petzo.domain.AdoptionRequest;
import com.petzo.domain.Pet;
import com.petzo.domain.Types.PetStatus;
import com.petzo.domain.Types.RequestStatus;
import com.petzo.storage.StorageClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

/**
 * Outbound webhooks to n8n: flow 1 moderation, flow 2 notification emails.
 * Payloads are built after commit (final updatedAt, row visible to n8n's callback) and sent in the
 * background. If n8n is down the pet simply stays pending_review (RNF-08).
 */
@Component
public class Events {

	private static final Logger log = LoggerFactory.getLogger(Events.class);

	private final PetzoProperties props;
	private final StorageClient storage;
	private final JdbcTemplate jdbc;
	private final AiService ai;
	private final RestClient http = RestClient.create();

	public Events(PetzoProperties props, StorageClient storage, JdbcTemplate jdbc, AiService ai) {
		this.props = props;
		this.storage = storage;
		this.jdbc = jdbc;
		this.ai = ai;
	}

	public void moderate(Pet pet) {
		// RNF-05/RNF-08: over the daily AI cap the pet just waits in the admin queue
		if (!ai.underDailyCap()) {
			log.info("AI daily cap reached; pet {} left for manual review", pet.id);
			return;
		}
		afterCommit(() -> {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("petId", pet.id);
			body.put("version", pet.version); // echoed back so results for an older version are ignored
			body.put("name", pet.name);
			body.put("species", pet.species);
			body.put("description", String.valueOf(pet.description));
			body.put("photos", pet.photos.stream().limit(5).map(p -> storage.publicUrl(p.storagePath)).toList());
			return send(props.n8nModerationUrl(), body);
		});
	}

	public void petStatusChanged(Pet pet) {
		if (pet.status != PetStatus.approved && pet.status != PetStatus.rejected) return;
		boolean ok = pet.status == PetStatus.approved;
		notify(pet.owner.id, "pet_" + pet.status,
				ok ? "¡" + pet.name + " ya está publicado en Petzo!" : "Tu publicación de " + pet.name + " necesita cambios",
				ok ? "Revisamos la publicación de " + pet.name + " y ya aparece en el catálogo."
						: "No pudimos aprobar la publicación de " + pet.name + ". Motivo: " + pet.moderationReason + "\nEdítala desde Mis publicaciones y vuelve a enviarla.");
	}

	public void requestCreated(AdoptionRequest r) {
		notify(r.pet.owner.id, "request_new", "Nueva solicitud para adoptar a " + r.pet.name, r.adopter.fullName + " quiere adoptar a " + r.pet.name + ". Revísala en Petzo.");
	}

	public void requestStatusChanged(AdoptionRequest r) {
		boolean ok = r.status == RequestStatus.approved;
		notify(r.adopter.id, "request_" + r.status,
				ok ? "¡Aprobaron tu solicitud para " + r.pet.name + "!" : "Tu solicitud para " + r.pet.name + " no fue aprobada",
				ok ? "Ya puedes ver el WhatsApp del publicador en Petzo para coordinar la adopción." : "Gracias por tu interés. Hay más peludos esperando un hogar en Petzo.");
	}

	private void notify(UUID userId, String event, String subject, String text) {
		afterCommit(() -> {
			String email = jdbc.query("select email from auth.users where id = ?", rs -> rs.next() ? rs.getString(1) : null, userId);
			return email == null ? null : send(props.n8nNotifyUrl(), Map.of("event", event, "to", email, "subject", subject, "text", text));
		});
	}

	/** Builds the payload once the transaction committed (or now, if none), then posts it off-thread. */
	private void afterCommit(java.util.function.Supplier<Runnable> build) {
		Runnable fire = () -> {
			try {
				Runnable post = build.get();
				if (post != null) CompletableFuture.runAsync(post);
			} catch (Exception e) {
				log.warn("could not build n8n payload: {}", e.getMessage());
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override public void afterCommit() { fire.run(); }
			});
		} else {
			fire.run();
		}
	}

	private Runnable send(String url, Object body) {
		if (url == null || url.isBlank()) {
			log.info("n8n webhook not configured; event skipped"); // body holds emails: never log it
			return null;
		}
		return () -> {
			try {
				// n8n webhooks require this header: nobody else can trigger moderation or emails
				http.post().uri(url).header("x-internal-secret", props.internalSecret()).body(body).retrieve().toBodilessEntity();
			} catch (Exception e) {
				log.warn("n8n webhook failed ({}): {}", url, e.getMessage());
			}
		};
	}
}
