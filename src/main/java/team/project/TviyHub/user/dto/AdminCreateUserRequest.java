package team.project.TviyHub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import team.project.TviyHub.user.Role;

public record AdminCreateUserRequest(
		@Schema(example = "Ivan Petrenko")
		@NotBlank @Size(max = 100) String name,
		@Schema(description = "Phone in international format", example = "+380501234567")
		@NotBlank @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "must be in international format, e.g. +380671234567") String phone,
		@Schema(description = "The email is marked as verified for users created by an admin", example = "ivan@example.com")
		@NotBlank @Email @Size(max = 255) String email,
		@Schema(description = "Password, 8 to 72 characters", example = "StrongPass123")
		@NotBlank @Size(min = 8, max = 72) String password,
		@Schema(description = "USER or ADMIN", example = "ADMIN")
		@NotNull Role role
) {
}
