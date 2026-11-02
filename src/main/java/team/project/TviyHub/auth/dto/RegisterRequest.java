package team.project.TviyHub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@Schema(description = "Full name", example = "Olena Shevchenko")
		@NotBlank @Size(max = 100) String name,
		@Schema(description = "Phone in international format", example = "+380671234567")
		@NotBlank @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "must be in international format, e.g. +380671234567") String phone,
		@Schema(description = "Email, stored in lower case; a verification code is sent to it", example = "olena@example.com")
		@NotBlank @Email @Size(max = 255) String email,
		@Schema(description = "Password, 8 to 72 characters", example = "StrongPass123")
		@NotBlank @Size(min = 8, max = 72) String password
) {
}
