package team.project.TviyHub.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
