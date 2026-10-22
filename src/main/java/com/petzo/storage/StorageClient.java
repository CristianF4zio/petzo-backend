package com.petzo.storage;

import com.petzo.config.PetzoProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/** Supabase Storage REST with the service_role key (backend only, SPEC §15). */
@Component
public class StorageClient {

	public static final String BUCKET = "pet-photos";

	public record SignedUpload(String path, String signedUrl, String token) {}

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

	public String publicUrl(String path) {
		return base + "/object/public/" + BUCKET + "/" + path;
	}

	@SuppressWarnings("unchecked")
	public SignedUpload signUpload(String path, boolean upsert) {
		Map<String, String> res = http.post()
				.uri("/object/upload/sign/" + BUCKET + "/" + path) // server-built path; a template var would encode "/"
				.header("x-upsert", String.valueOf(upsert))
				.body(Map.of())
				.retrieve()
				.body(Map.class);
		String url = base + res.get("url");
		String token = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("token");
		return new SignedUpload(path, url, token);
	}

	/** Server-side upload (Instagram imports); app uploads go through signed URLs. */
	public void upload(String path, byte[] bytes, String contentType) {
		http.post().uri("/object/" + BUCKET + "/" + path).contentType(org.springframework.http.MediaType.parseMediaType(contentType))
				.body(bytes).retrieve().toBodilessEntity();
	}

	public boolean exists(String path) {
		try {
			http.head().uri("/object/public/" + BUCKET + "/" + path).retrieve().toBodilessEntity();
			return true;
		} catch (RestClientResponseException e) {
			return false;
		}
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
