package team.project.TviyHub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: a null field is left unchanged. Role, block status and password have
 * their own endpoints.
 */
public record AdminUpdateUserRequest(
		@Schema(description = "New name; omit to keep the current one", example = "Ivan Petrenko")
		@Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
		@Schema(description = "New phone in international format; omit to keep the current one", example = "+380501234567")
		@Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "must be in international format, e.g. +380671234567") String phone,
		@Schema(description = "New email, marked as verified; omit to keep the current one", example = "ivan@example.com")
		@Email @Size(max = 255) String email
) {
}
