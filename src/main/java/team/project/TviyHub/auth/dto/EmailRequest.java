package team.project.TviyHub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Request that only carries an email address (resend verification, forgot password). */
public record EmailRequest(
		@Schema(description = "Account email", example = "olena@example.com")
		@NotBlank String email
) {
}
