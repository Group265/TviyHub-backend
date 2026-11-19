package team.project.TviyHub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.project.TviyHub.validation.PasswordsMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@PasswordsMatch(password = "newPassword", confirmation = "confirmNewPassword")
public record ChangePasswordRequest(
		@Schema(description = "Current password", example = "StrongPass123")
		@NotBlank String currentPassword,
		@Schema(description = "New password, 8 to 72 characters; applied after the emailed code is confirmed",
				example = "NewStrongPass456")
		@NotBlank @Size(min = 8, max = 72) String newPassword,
		@Schema(description = "The new password typed a second time; must be equal to newPassword", example = "NewStrongPass456")
		@NotBlank String confirmNewPassword
) {
}
