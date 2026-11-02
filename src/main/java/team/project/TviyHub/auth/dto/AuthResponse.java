package team.project.TviyHub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
		@Schema(description = "JWT for the Authorization: Bearer header; short-lived (15 minutes by default)",
				example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature")
		String accessToken,
		@Schema(description = "Always Bearer", example = "Bearer")
		String tokenType,
		@Schema(description = "Access token lifetime in seconds", example = "900")
		long expiresInSeconds,
		@Schema(description = "Opaque token for POST /api/auth/refresh; each use returns a new one and invalidates this one",
				example = "k3y9Tz0q2mJ7vQw1XbN8cR5aLfH4sD6uEoPiYtGdZxA")
		String refreshToken,
		@Schema(description = "Refresh token lifetime in seconds", example = "2592000")
		long refreshExpiresInSeconds
) {
}
