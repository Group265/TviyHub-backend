package team.project.TviyHub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
		@Schema(description = "The refresh token received from login or the previous refresh",
				example = "k3y9Tz0q2mJ7vQw1XbN8cR5aLfH4sD6uEoPiYtGdZxA")
		@NotBlank @Size(max = 200) String refreshToken
) {
}
