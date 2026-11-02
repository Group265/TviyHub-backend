package team.project.TviyHub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@Schema(description = "Current password", example = "StrongPass123")
		@NotBlank String currentPassword,
		@Schema(description = "New password, 8 to 72 characters; applied after the emailed code is confirmed",
				example = "NewStrongPass456")
		@NotBlank @Size(min = 8, max = 72) String newPassword
) {
}
