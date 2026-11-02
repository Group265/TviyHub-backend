package team.project.TviyHub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
		@Schema(description = "New password, 8 to 72 characters; all sessions of the user are logged out",
				example = "NewStrongPass456")
		@NotBlank @Size(min = 8, max = 72) String newPassword
) {
}
