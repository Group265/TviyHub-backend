package team.project.TviyHub.booking;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import team.project.TviyHub.booking.dto.BookingRequestDto;
import team.project.TviyHub.booking.dto.BookingResponseDto;
import team.project.TviyHub.config.MapStructConfig;
import team.project.TviyHub.user.User;
import team.project.TviyHub.workspace.Workspace;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Mapper(config = MapStructConfig.class)
public interface BookingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bookingType", source = "dto.bookingType")
    @Mapping(target = "bookingDate", ignore = true)
    @Mapping(target = "startTime", source = "dto.startTime")
    @Mapping(target = "endTime", source = "dto.endTime")
    @Mapping(target = "status", constant = "PENDING_PAYMENT")
    @Mapping(target = "payment", ignore = true)
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Booking toEntity(BookingRequestDto dto, User user, Workspace workspace);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "workspaceId", source = "workspace.id")
    @Mapping(target = "workspaceName", source = "workspace.name")
    @Mapping(target = "paymentId", source = "payment.id")
    @Mapping(target = "paymentStatus", source = "booking.payment.status")
    @Mapping(target = "paymentDeadline", expression = "java(calculatePaymentDeadline(booking))")
    BookingResponseDto toDto(Booking booking);

    default LocalDateTime calculatePaymentDeadline(Booking booking) {
        if (booking.getCreatedAt() == null) {
            return null;
        }
        LocalTime start = booking.getStartTime() != null ? booking.getStartTime() : LocalTime.of(9, 0);
        LocalDateTime bookingStart = LocalDateTime.of(booking.getBookingDate(), start);
        LocalDateTime plus24Hours = booking.getCreatedAt().plusHours(24);
        return plus24Hours.isBefore(bookingStart) ? plus24Hours : bookingStart;
    }
}
