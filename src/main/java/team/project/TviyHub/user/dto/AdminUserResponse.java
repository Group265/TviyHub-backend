package team.project.TviyHub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;

import java.time.Instant;

public record AdminUserResponse(
		@Schema(example = "1") Long id,
		@Schema(example = "Olena Shevchenko") String name,
		@Schema(example = "+380671234567") String phone,
		@Schema(example = "olena@example.com") String email,
		@Schema(description = "USER or ADMIN") Role role,
		@Schema(description = "A blocked user cannot log in or refresh tokens", example = "false")
		boolean blocked,
		@Schema(example = "true") boolean emailVerified,
		@Schema(example = "2026-11-02T09:15:30Z") Instant createdAt
) {

	public static AdminUserResponse from(User user) {
		return new AdminUserResponse(user.getId(), user.getName(), user.getPhone(), user.getEmail(),
				user.getRole(), user.isBlocked(), user.isEmailVerified(), user.getCreatedAt());
	}
}
