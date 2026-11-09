package team.project.TviyHub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(
		@Schema(description = "ID token (the credential field) from Google Sign-In on the frontend",
				example = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjEyMyIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NSJ9.signature")
		@NotBlank @Size(max = 4000) String idToken
) {
}
