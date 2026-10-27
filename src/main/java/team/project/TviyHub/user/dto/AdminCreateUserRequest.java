package team.project.TviyHub.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import team.project.TviyHub.user.Role;

public record AdminCreateUserRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "must be in international format, e.g. +380671234567") String phone,
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(min = 8, max = 72) String password,
		@NotNull Role role
) {
}
