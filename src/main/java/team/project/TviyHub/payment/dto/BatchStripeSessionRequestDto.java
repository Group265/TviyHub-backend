package team.project.TviyHub.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchStripeSessionRequestDto(
        @NotEmpty(message = "Payment IDs list cannot be empty")
        List<Long> paymentIds,

        @NotBlank(message = "Success URL is required")
        String successUrl,

        @NotBlank(message = "Cancel URL is required")
        String cancelUrl
) {}
