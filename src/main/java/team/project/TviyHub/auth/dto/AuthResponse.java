package team.project.TviyHub.auth.dto;

public record AuthResponse(
		String accessToken,
		String tokenType,
		long expiresInSeconds,
		String refreshToken,
		long refreshExpiresInSeconds
) {
}
