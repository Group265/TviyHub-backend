package team.project.TviyHub.user.dto;

import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;

import java.time.Instant;

public record UserResponse(
		Long id,
		String name,
		String phone,
		String email,
		Role role,
		Instant createdAt
) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getPhone(),
				user.getEmail(), user.getRole(), user.getCreatedAt());
	}
}
