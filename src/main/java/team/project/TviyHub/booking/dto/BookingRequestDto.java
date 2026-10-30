package team.project.TviyHub.booking.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import team.project.TviyHub.tariff.Tariff;

public record BookingRequestDto(
        @NotNull(message = "Workspace ID is required")
        Long workspaceId,

        @NotNull(message = "Booking type is required")
        Tariff.BookingType bookingType,

        @NotNull(message = "Booking date is required")
        @FutureOrPresent(message = "Booking date cannot be in the past")
        LocalDate bookingDate,

        LocalTime startTime,
        LocalTime endTime
) {}
