package team.project.TviyHub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;

import java.time.Instant;

public record UserProfileResponse(
		@Schema(example = "1") Long id,
		@Schema(example = "Olena Shevchenko") String name,
		@Schema(description = "Null for Google accounts until a phone is added", example = "+380671234567", nullable = true) String phone,
		@Schema(example = "olena@example.com") String email,
		@Schema(description = "USER or ADMIN") Role role,
		@Schema(description = "False until the code from the registration email is confirmed; password, phone and email changes require true",
				example = "true")
		boolean emailVerified,
		@Schema(example = "2026-11-02T09:15:30Z") Instant createdAt
) {

	public static UserProfileResponse from(User user) {
		return new UserProfileResponse(user.getId(), user.getName(), user.getPhone(),
				user.getEmail(), user.getRole(), user.isEmailVerified(), user.getCreatedAt());
	}
}
