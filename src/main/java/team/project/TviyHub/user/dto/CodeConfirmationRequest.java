package team.project.TviyHub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CodeConfirmationRequest(
		@Schema(description = "6-digit code from the email (valid for 10 minutes, 5 attempts)", example = "482913")
		@NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code") String code
) {
}
