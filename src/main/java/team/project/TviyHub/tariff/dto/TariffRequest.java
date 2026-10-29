package team.project.TviyHub.tariff.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import team.project.TviyHub.tariff.Tariff;
import team.project.TviyHub.workspace.Workspace;

public record TariffRequest(
        @NotNull(message = "Resource type is required")
        Workspace.WorkspaceType workspaceType,

        @NotNull(message = "Booking type is required")
        Tariff.BookingType bookingType,

        @NotNull(message = "Price is required")
        @PositiveOrZero(message = "Price cannot be negative")
        BigDecimal price
) {}
