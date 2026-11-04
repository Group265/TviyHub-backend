package team.project.TviyHub.subscription.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SubscriptionRequest(

        @NotNull(message = "Workspace ID cannot be null")
        Long workspaceId,

        @NotNull(message = "Start date cannot be null")
        LocalDate startDate
) {}
