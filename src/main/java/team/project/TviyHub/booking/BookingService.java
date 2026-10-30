package team.project.TviyHub.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.booking.dto.BookingRequestDto;
import team.project.TviyHub.booking.dto.BookingResponseDto;
import team.project.TviyHub.closedday.ClosedDayService;
import team.project.TviyHub.exception.BadRequestException;
import team.project.TviyHub.exception.ConflictException;
import team.project.TviyHub.exception.ForbiddenException;
import team.project.TviyHub.exception.ResourceNotFoundException;
import team.project.TviyHub.subscription.Subscription;
import team.project.TviyHub.subscription.SubscriptionRepository;
import team.project.TviyHub.tariff.Tariff;
import team.project.TviyHub.tariff.TariffRepository;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.workspace.Workspace;
import team.project.TviyHub.workspace.WorkspaceRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TariffRepository tariffRepository;
    private final ClosedDayService closedDayService;
    private final SubscriptionRepository subscriptionRepository;
    private final BookingMapper bookingMapper;

    @Transactional(readOnly = true)
    public List<BookingResponseDto> getAllBookings(Long filterUserId, User currentUser) {
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        List<Booking> bookings;

        if (isAdmin) {
            if (filterUserId != null) {
                bookings = bookingRepository.findByUserId(filterUserId);
            } else {
                bookings = bookingRepository.findAll();
            }
        } else {
            bookings = bookingRepository.findByUserId(currentUser.getId());
        }

        return bookings.stream()
                .map(bookingMapper::toDto)
                .toList();
    }

    @Transactional
    public BookingResponseDto createBooking(BookingRequestDto requestDto, User currentUser) {
        Workspace workspace = workspaceRepository.findById(requestDto.workspaceId())
                .filter(w -> !w.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace with ID " + requestDto.workspaceId() + " not found"));

        LocalDate bookingDate = requestDto.bookingDate();

        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (closedDayService.isDateClosed(bookingDate)) {
            throw new ConflictException("The hub is closed on " + bookingDate + " (holiday, repair or special event)");
        }

        if (!isAdmin) {
            java.time.DayOfWeek dayOfWeek = bookingDate.getDayOfWeek();
            if (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                throw new BadRequestException("The hub is only open from Monday to Friday. Weekend booking is unavailable for clients");
            }
        }

        boolean isDaily = requestDto.bookingType() == Tariff.BookingType.DAILY;
        long hours = 0;

        BookingRequestDto finalRequestDto;

        if (!isDaily) {
            LocalTime start = requestDto.startTime();
            LocalTime end = requestDto.endTime();

            if (start == null || end == null || !end.isAfter(start)) {
                throw new BadRequestException("Invalid time slot: end time must be after start time");
            }

            if (start.getMinute() != 0 || end.getMinute() != 0) {
                throw new BadRequestException("Hourly booking is restricted to fixed 1-hour slots. Minutes must be 00.");
            }

            if (!isAdmin) {
                if (start.isBefore(LocalTime.of(9, 0)) || end.isAfter(LocalTime.of(19, 0))) {
                    throw new BadRequestException("Clients can only book between 09:00 and 19:00");
                }
            }

            hours = Duration.between(start, end).toHours();
            if (hours < 1 || hours > 4) {
                throw new BadRequestException("Hourly booking must be between 1 and 4 hours");
            }

            finalRequestDto = requestDto;
        } else {
            finalRequestDto = new BookingRequestDto(
                    requestDto.workspaceId(),
                    requestDto.bookingType(),
                    requestDto.bookingDate(),
                    LocalTime.of(9, 0),
                    LocalTime.of(19, 0)
            );
        }

        if (workspace.getType() == Workspace.WorkspaceType.DESK) {
            List<Subscription> activeSubs = subscriptionRepository.findOverlappingSubscriptions(
                    workspace.getId(),
                    finalRequestDto.bookingDate()
            );
            if (!activeSubs.isEmpty()) {
                throw new ConflictException("This desk is occupied by an active subscription for this date");
            }
        }

        List<Booking> activeBookings = bookingRepository.findActiveBookingsByWorkspaceAndDate(
                workspace.getId(),
                finalRequestDto.bookingDate()
        );

        for (Booking existing : activeBookings) {
            if (existing.getBookingType() == Tariff.BookingType.DAILY) {
                throw new ConflictException("Workspace is already booked for the whole day (DAILY)");
            }
            if (isDaily) {
                throw new ConflictException("Cannot book for the whole day. Hourly bookings already exist");
            }
            if (finalRequestDto.startTime().isBefore(existing.getEndTime()) && finalRequestDto.endTime().isAfter(existing.getStartTime())) {
                throw new ConflictException("Selected time slot overlaps with an existing booking");
            }
        }

        Tariff tariff = tariffRepository.findByWorkspaceTypeAndBookingType(workspace.getType(), finalRequestDto.bookingType())
                .orElseThrow(() -> new ResourceNotFoundException("Tariff not found for this resource and booking type"));

        BigDecimal totalPrice = isDaily
                ? tariff.getPrice()
                : tariff.getPrice().multiply(BigDecimal.valueOf(hours));

        Booking booking = bookingMapper.toEntity(finalRequestDto, currentUser, workspace);
        booking.setTotalPrice(totalPrice);

        Booking savedBooking = bookingRepository.save(booking);
        return bookingMapper.toDto(savedBooking);
    }


    @Transactional
    public BookingResponseDto cancelBooking(Long bookingId, User currentUser) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking with ID " + bookingId + " not found"));

        if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            throw new BadRequestException("Booking is already cancelled");
        }

        if (currentUser.getRole() != Role.ADMIN) {
            if (!booking.getUser().getId().equals(currentUser.getId())) {
                throw new ForbiddenException("You can only cancel your own bookings");
            }

            LocalDate today = LocalDate.now();

            if (booking.getBookingType() == Tariff.BookingType.DAILY) {
                if (!today.isBefore(booking.getBookingDate())) {
                    throw new BadRequestException("Daily bookings can only be cancelled at least 1 day in advance");
                }
            } else if (booking.getBookingType() == Tariff.BookingType.HOURLY) {
                if (!today.equals(booking.getBookingDate())) {
                    if (today.isAfter(booking.getBookingDate())) {
                        throw new BadRequestException("Cannot cancel a past booking");
                    }
                } else {
                    LocalTime now = LocalTime.now();
                    if (now.plusHours(2).isAfter(booking.getStartTime())) {
                        throw new BadRequestException("Hourly bookings can only be cancelled at least 2 hours before the start time");
                    }
                }
            }
        }

        booking.setStatus(Booking.BookingStatus.CANCELLED);

        if ("PAID".equals(booking.getPaymentStatus())) {
            booking.setPaymentStatus("REFUND_PENDING");
        }

        Booking saved = bookingRepository.save(booking);
        return bookingMapper.toDto(saved);
    }
}
