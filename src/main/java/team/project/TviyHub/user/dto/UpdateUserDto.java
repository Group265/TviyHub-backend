package team.project.TviyHub.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: a null field is left unchanged. Role, phone, email and password are
 * intentionally absent: phone, email and password are changed through the code-confirmed flows.
 */
public record UpdateUserDto(
		@Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name
) {
}
