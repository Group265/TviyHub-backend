package team.project.TviyHub.user.dto;

import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;

import java.time.Instant;

public record UserProfileResponse(
		Long id,
		String name,
		String phone,
		String email,
		Role role,
		Instant createdAt
) {

	public static UserProfileResponse from(User user) {
		return new UserProfileResponse(user.getId(), user.getName(), user.getPhone(),
				user.getEmail(), user.getRole(), user.getCreatedAt());
	}
}
