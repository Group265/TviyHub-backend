package team.project.TviyHub.gallery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.project.TviyHub.gallery.GalleryImageView;

import java.time.Instant;

public record GalleryImageResponse(
		@Schema(example = "1") Long id,
		@Schema(description = "Optional caption", example = "Coworking hall on the first floor") String caption,
		@Schema(description = "Detected format: image/jpeg, image/png or image/webp", example = "image/jpeg") String contentType,
		@Schema(description = "File size in bytes", example = "245760") int sizeBytes,
		@Schema(description = "Address of the image itself; open it in the browser or an img tag",
				example = "/api/gallery/1/image")
		String url,
		@Schema(example = "2026-11-04T09:15:30Z") Instant createdAt
) {

	public static GalleryImageResponse from(GalleryImageView view) {
		return new GalleryImageResponse(view.getId(), view.getCaption(), view.getContentType(), view.getSizeBytes(),
				"/api/gallery/" + view.getId() + "/image", view.getCreatedAt());
	}
}
