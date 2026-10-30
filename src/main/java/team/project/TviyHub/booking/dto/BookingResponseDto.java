package team.project.TviyHub.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import team.project.TviyHub.booking.Booking;
import team.project.TviyHub.tariff.Tariff;

public record BookingResponseDto(
        Long id,
        Long userId,
        Long workspaceId,
        String workspaceName,
        Tariff.BookingType bookingType,
        LocalDate bookingDate,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal totalPrice,
        Booking.BookingStatus status,
        String paymentStatus
) {}
