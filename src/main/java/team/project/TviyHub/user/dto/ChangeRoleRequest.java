package team.project.TviyHub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import team.project.TviyHub.user.Role;

public record ChangeRoleRequest(
		@Schema(description = "USER or ADMIN; an admin cannot change their own role", example = "ADMIN")
		@NotNull Role role
) {
}
