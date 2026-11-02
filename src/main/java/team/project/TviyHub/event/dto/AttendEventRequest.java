package team.project.TviyHub.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record AttendEventRequest(
		@Schema(description = "Optional message for the admin", example = "I would like to attend with a colleague")
		@Size(max = 500) String comment
) {
}
