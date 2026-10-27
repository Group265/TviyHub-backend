package team.project.TviyHub.user.dto;

import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;

import java.time.Instant;

public record AdminUserResponse(
		Long id,
		String name,
		String phone,
		String email,
		Role role,
		boolean blocked,
		Instant createdAt
) {

	public static AdminUserResponse from(User user) {
		return new AdminUserResponse(user.getId(), user.getName(), user.getPhone(), user.getEmail(),
				user.getRole(), user.isBlocked(), user.getCreatedAt());
	}
}
