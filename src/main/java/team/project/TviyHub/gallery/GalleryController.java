package team.project.TviyHub.gallery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team.project.TviyHub.gallery.dto.GalleryImageResponse;
import team.project.TviyHub.user.dto.PageResponse;

import java.time.Duration;

@RestController
@RequestMapping("/api/gallery")
@RequiredArgsConstructor
@Tag(name = "Gallery", description = "Photos of the hub (public)")
public class GalleryController {

	private final GalleryService galleryService;

	@GetMapping
	@Operation(summary = "List gallery photos",
			description = "Newest first. Each item has a url to open the image itself.")
	public PageResponse<GalleryImageResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return galleryService.list(page, size);
	}

	@GetMapping("/{id}/image")
	@Operation(summary = "Get the image file",
			description = "Returns the JPG, PNG or WebP bytes with the matching Content-Type.")
	@ApiResponse(responseCode = "200", description = "The image",
			content = {@Content(mediaType = "image/jpeg"), @Content(mediaType = "image/png"),
					@Content(mediaType = "image/webp")})
	@ApiResponse(responseCode = "404", description = "Image not found")
	public ResponseEntity<byte[]> image(@PathVariable Long id) {
		GalleryImage image = galleryService.getImage(id);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(image.getContentType()))
				.cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
				.eTag("\"" + image.getId() + "-" + image.getCreatedAt().toEpochMilli() + "\"")
				.body(image.getData());
	}
}
