package team.project.TviyHub.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: a null field is left unchanged. Role, block status and password have
 * their own endpoints.
 */
public record AdminUpdateUserRequest(
		@Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
		@Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "must be in international format, e.g. +380671234567") String phone,
		@Email @Size(max = 255) String email
) {
}
