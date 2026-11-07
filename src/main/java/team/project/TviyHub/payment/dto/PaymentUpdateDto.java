package team.project.TviyHub.payment.dto;

import jakarta.validation.constraints.NotNull;
import team.project.TviyHub.payment.Payment;

public record PaymentUpdateDto(
        @NotNull(message = "Payment status cannot be null")
        Payment.PaymentStatus status,

        @NotNull(message = "Payment method cannot be null")
        Payment.PaymentMethod paymentMethod
) {}
