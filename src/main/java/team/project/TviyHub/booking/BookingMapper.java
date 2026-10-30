package team.project.TviyHub.booking;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import team.project.TviyHub.booking.dto.BookingRequestDto;
import team.project.TviyHub.booking.dto.BookingResponseDto;
import team.project.TviyHub.config.MapStructConfig;
import team.project.TviyHub.user.User;
import team.project.TviyHub.workspace.Workspace;

@Mapper(config = MapStructConfig.class)
public interface BookingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bookingType", source = "dto.bookingType")
    @Mapping(target = "bookingDate", source = "dto.bookingDate")
    @Mapping(target = "startTime", source = "dto.startTime")
    @Mapping(target = "endTime", source = "dto.endTime")
    @Mapping(target = "status", constant = "CONFIRMED")
    @Mapping(target = "paymentStatus", constant = "UNPAID")
    @Mapping(target = "totalPrice", ignore = true)
    Booking toEntity(BookingRequestDto dto, User user, Workspace workspace);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "workspaceId", source = "workspace.id")
    @Mapping(target = "workspaceName", source = "workspace.name")
    BookingResponseDto toDto(Booking booking);
}
