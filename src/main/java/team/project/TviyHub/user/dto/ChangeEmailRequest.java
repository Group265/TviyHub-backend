package team.project.TviyHub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeEmailRequest(
		@Schema(description = "New email; the confirmation code is sent to this address", example = "olena.new@example.com")
		@NotBlank @Email @Size(max = 255) String newEmail,
		@Schema(description = "Current password", example = "StrongPass123")
		@NotBlank String currentPassword
) {
}
