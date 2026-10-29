package team.project.TviyHub.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePhoneRequest(
		@NotBlank @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "must be in international format, e.g. +380671234567") String newPhone
) {
}
