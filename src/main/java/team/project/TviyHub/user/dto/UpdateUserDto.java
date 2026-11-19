package team.project.TviyHub.user.dto;

import team.project.TviyHub.validation.PersonName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: a null field is left unchanged. Role, phone, email and password are
 * intentionally absent: phone, email and password are changed through the code-confirmed flows.
 */
public record UpdateUserDto(
		@Schema(description = "New name; omit to keep the current one. Only English, Ukrainian or Russian letters; words may be separated by a space, hyphen or apostrophe", example = "Olena Kovalenko")
		@Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Pattern(regexp = PersonName.REGEX, message = PersonName.MESSAGE) String name
) {
}
