package team.project.TviyHub.payment.dto;

public record StripeSessionResponseDto(
   String sessionUrl,
   String sessionId
) {}
