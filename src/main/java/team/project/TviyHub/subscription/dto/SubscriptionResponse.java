package team.project.TviyHub.subscription.dto;

import team.project.TviyHub.payment.Payment;
import team.project.TviyHub.subscription.Subscription;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SubscriptionResponse(
        Long id,
        Long userId,
        Long workspaceId,
        LocalDate startDate,
        LocalDate endDate,
        Subscription.SubscriptionStatus status,
        Payment.PaymentStatus paymentStatus,
        LocalDateTime createdAt
) {}
