package team.project.TviyHub.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Optional explanation shown to the user when a request is rejected. */
public record ReviewEventRequest(
		@Schema(description = "Why the request was rejected; sent to the user by email", example = "The hub is closed for repairs")
		@Size(max = 500) String reason
) {
}
