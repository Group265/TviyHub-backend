package team.project.TviyHub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePhoneRequest(
		@Schema(description = "New phone in international format; the confirmation code is sent to the account email",
				example = "+380991112233")
		@NotBlank @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "must be in international format, e.g. +380671234567") String newPhone
) {
}
