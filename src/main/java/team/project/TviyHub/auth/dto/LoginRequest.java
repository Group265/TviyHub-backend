package team.project.TviyHub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@Schema(description = "Email used at registration (case-insensitive)", example = "olena@example.com")
		@NotBlank String email,
		@Schema(description = "Account password", example = "StrongPass123")
		@NotBlank String password
) {
}
