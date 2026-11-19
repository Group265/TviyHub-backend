package team.project.TviyHub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.project.TviyHub.validation.PasswordsMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@PasswordsMatch(password = "newPassword", confirmation = "confirmNewPassword")
public record ResetPasswordWithCodeRequest(
		@Schema(description = "Account email", example = "olena@example.com")
		@NotBlank String email,
		@Schema(description = "6-digit code from the password reset email", example = "482913")
		@NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code") String code,
		@Schema(description = "New password, 8 to 72 characters; all sessions are logged out", example = "NewStrongPass456")
		@NotBlank @Size(min = 8, max = 72) String newPassword,
		@Schema(description = "The new password typed a second time; must be equal to newPassword", example = "NewStrongPass456")
		@NotBlank String confirmNewPassword
) {
}
