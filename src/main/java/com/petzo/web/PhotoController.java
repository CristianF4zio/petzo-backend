package com.petzo.web;

import com.petzo.storage.StorageClient;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Photos for the app and the panel come from here, so clients never connect to Supabase Storage. */
@RestController
public class PhotoController {

	private final StorageClient storage;

	public PhotoController(StorageClient storage) {
		this.storage = storage;
	}

	@GetMapping("/api/photos/**")
	ResponseEntity<byte[]> photo(HttpServletRequest req) {
		String path = req.getRequestURI().substring((req.getContextPath() + "/api/photos/").length());
		// Only our two folders and plain file names: no traversal, no other buckets
		if (!path.matches("(pets/[0-9a-f-]{36}|avatars)/[0-9a-f-]{36}\\.jpg")) return ResponseEntity.notFound().build();
		return storage.download(path)
				.map(r -> ResponseEntity.ok().contentType(org.springframework.http.MediaType.IMAGE_JPEG)
						.cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic()).body(r.getBody()))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
