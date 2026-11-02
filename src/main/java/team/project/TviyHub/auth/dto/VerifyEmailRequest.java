package team.project.TviyHub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
		@Schema(description = "Email that received the code", example = "olena@example.com")
		@NotBlank String email,
		@Schema(description = "6-digit code from the email (valid for 10 minutes)", example = "482913")
		@NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code") String code
) {
}
