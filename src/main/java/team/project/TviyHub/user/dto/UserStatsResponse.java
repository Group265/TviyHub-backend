package team.project.TviyHub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserStatsResponse(
		@Schema(description = "All users", example = "128") long total,
		@Schema(description = "Users with the ADMIN role", example = "3") long admins,
		@Schema(description = "Blocked users", example = "2") long blocked
) {
}
