package team.project.TviyHub.user.dto;

import team.project.TviyHub.validation.MaxUtf8Bytes;
import io.swagger.v3.oas.annotations.media.Schema;
import team.project.TviyHub.validation.PasswordsMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@PasswordsMatch(password = "newPassword", confirmation = "confirmNewPassword")
public record ChangePasswordRequest(
		@Schema(description = "Current password", example = "StrongPass123")
		@NotBlank String currentPassword,
		@Schema(description = "New password, 8 to 72 characters (at most 72 bytes in UTF-8: English letters take 1 byte, Ukrainian and Russian 2); applied after the emailed code is confirmed",
				example = "NewStrongPass456")
		@NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72) String newPassword,
		@Schema(description = "The new password typed a second time; must be equal to newPassword", example = "NewStrongPass456")
		@NotBlank String confirmNewPassword
) {
}
