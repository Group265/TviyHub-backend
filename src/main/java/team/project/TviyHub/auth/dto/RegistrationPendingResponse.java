package team.project.TviyHub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The registration waits for the code that was emailed; the account does not exist yet")
public record RegistrationPendingResponse(
		@Schema(description = "The address the code was sent to; send it back with the code to POST /api/auth/verify-email",
				example = "olena@example.com") String email,
		@Schema(description = "How long the code is valid", example = "600") long codeExpiresInSeconds
) {
}
