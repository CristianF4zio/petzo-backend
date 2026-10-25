package com.petzo.storage;

import com.petzo.config.PetzoProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Supabase Storage REST with the service_role key (backend only, SPEC §15). */
@Component
public class StorageClient {

	private static final Logger log = LoggerFactory.getLogger(StorageClient.class);

	public static final String BUCKET = "pet-photos";

	private final RestClient http;
	private final String base;

	public StorageClient(PetzoProperties props) {
		this.base = props.supabaseUrl() + "/storage/v1";
		this.http = RestClient.builder()
				.baseUrl(base)
				.defaultHeader("apikey", props.serviceRoleKey())
				.defaultHeader("Authorization", "Bearer " + props.serviceRoleKey())
				.build();
	}

	/** Supabase's own URL: only for server-side consumers (n8n moderation), never sent to the app or the panel. */
	public String publicUrl(String path) {
		return base + "/object/public/" + BUCKET + "/" + path;
	}

	/** URL the app and the panel load a photo from: this API (GET /api/photos/**), so clients never touch Storage. */
	public String clientUrl(String path) {
		String origin = RequestContextHolder.getRequestAttributes() == null ? "" : ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
		return origin + "/api/photos/" + path;
	}

	/** Stored avatar URLs pointing at our bucket become API URLs; Google avatars stay as they are. */
	public String clientAvatar(String avatarUrl) {
		String prefix = base + "/object/public/" + BUCKET + "/";
		return avatarUrl != null && avatarUrl.startsWith(prefix) ? clientUrl(avatarUrl.substring(prefix.length())) : avatarUrl;
	}

	/** Server-side upload (app photos, avatars, imports); upsert so an avatar can be replaced. */
	public void upload(String path, byte[] bytes, String contentType) {
		http.post().uri("/object/" + BUCKET + "/" + path).header("x-upsert", "true").contentType(org.springframework.http.MediaType.parseMediaType(contentType))
				.body(bytes).retrieve().toBodilessEntity();
	}

	/** Bytes and content type for the photo proxy; empty when the file is missing. */
	public java.util.Optional<org.springframework.http.ResponseEntity<byte[]>> download(String path) {
		try {
			return java.util.Optional.of(http.get().uri("/object/public/" + BUCKET + "/" + path).retrieve().toEntity(byte[].class));
		} catch (RestClientResponseException e) {
			return java.util.Optional.empty();
		}
	}

	/** RNF-06: the app compresses to ~500 KB; 2 MB and real JPEG bytes are checked here, not trusted from the client. */
	public static byte[] readJpeg(org.springframework.web.multipart.MultipartFile file) {
		var invalid = new com.petzo.common.ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PHOTOS", "Sube una foto JPEG de hasta 2 MB");
		if (file == null || file.isEmpty() || file.getSize() > 2_000_000) throw invalid;
		try {
			byte[] b = file.getBytes();
			if (b.length < 3 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8 || (b[2] & 0xFF) != 0xFF) throw invalid;
			return b;
		} catch (java.io.IOException e) {
			throw invalid;
		}
	}

	public boolean exists(String path) {
		try {
			http.head().uri("/object/public/" + BUCKET + "/" + path).retrieve().toBodilessEntity();
			return true;
		} catch (RestClientResponseException e) {
			return false;
		}
	}

	/** Deletes only once the DB change committed, so a rollback never leaves rows pointing at missing files. */
	public void deleteAfterCommit(List<String> paths) {
		if (paths.isEmpty()) return;
		if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
			delete(paths);
			return;
		}
		org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
			// The change is already saved: a Storage hiccup is logged, not thrown (it would also skip later afterCommit hooks like emails)
			@Override public void afterCommit() {
				try {
					delete(paths);
				} catch (Exception e) {
					log.warn("Storage delete failed for {} paths: {}", paths.size(), e.getMessage());
				}
			}
		});
	}

	public void delete(List<String> paths) {
		if (paths.isEmpty()) return;
		http.method(org.springframework.http.HttpMethod.DELETE)
				.uri("/object/{bucket}", BUCKET)
				.body(Map.of("prefixes", paths))
				.retrieve()
				.toBodilessEntity();
	}
}
