package team.project.TviyHub.user.dto;

import team.project.TviyHub.validation.MaxUtf8Bytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
		@Schema(description = "New password, 8 to 72 characters (at most 72 bytes in UTF-8: English letters take 1 byte, Ukrainian and Russian 2); all sessions of the user are logged out",
				example = "NewStrongPass456")
		@NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72) String newPassword
) {
}
