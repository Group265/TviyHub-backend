package team.project.TviyHub.gallery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import team.project.TviyHub.config.OpenApiConfig;
import team.project.TviyHub.gallery.dto.GalleryImageResponse;
import team.project.TviyHub.user.User;

@RestController
@RequestMapping("/api/admin/gallery")
@RequiredArgsConstructor
@Tag(name = "Admin: gallery", description = "Upload and delete gallery photos (ADMIN role only)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponse(responseCode = "401", description = "Authentication required")
@ApiResponse(responseCode = "403", description = "ADMIN role required")
@PreAuthorize("hasRole('ADMIN')")
public class AdminGalleryController {

	private final GalleryService galleryService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Upload a photo",
			description = "multipart/form-data with the part file (JPG, PNG or WebP, up to 5 MB) and an optional caption. "
					+ "The format is checked by the file content, not by its name.")
	@ApiResponse(responseCode = "400", description = "No file, empty file, unsupported format or caption too long")
	@ApiResponse(responseCode = "413", description = "The file is larger than 5 MB")
	public GalleryImageResponse upload(
			@AuthenticationPrincipal User admin,
			@Parameter(description = "The image file") @RequestPart(value = "file", required = false) MultipartFile file,
			@Parameter(description = "Optional caption, up to 200 characters", example = "Coworking hall on the first floor")
			@RequestParam(value = "caption", required = false) String caption) {
		return galleryService.upload(admin.getId(), file, caption);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a photo")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiResponse(responseCode = "404", description = "Image not found")
	public void delete(@PathVariable Long id) {
		galleryService.delete(id);
	}
}
