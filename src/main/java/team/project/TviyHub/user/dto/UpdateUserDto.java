package team.project.TviyHub.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: a null field is left unchanged. Role and other protected fields are
 * intentionally absent, so they cannot be changed through this DTO.
 */
public record UpdateUserDto(
		@Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
		@Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "must be in international format, e.g. +380671234567") String phone
) {
}
