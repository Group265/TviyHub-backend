package team.project.TviyHub.payment.dto;

import team.project.TviyHub.payment.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponseDto(
        Long id,
        Payment.PaymentStatus status,
        BigDecimal amount,
        LocalDateTime paidAt,
        Payment.PaymentMethod paymentMethod,
        String externalTransactionId,
        Long bookingId,
        Long subscriptionId
) {}
