package team.project.TviyHub.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Request that only carries an email address (resend verification, forgot password). */
public record EmailRequest(@NotBlank String email) {
}
