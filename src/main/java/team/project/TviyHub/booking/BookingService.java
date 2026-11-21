package team.project.TviyHub.booking;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.booking.dto.BookingRequestDto;
import team.project.TviyHub.booking.dto.BookingResponseDto;
import team.project.TviyHub.booking.dto.WorkspaceAvailabilityDto;
import team.project.TviyHub.closedday.ClosedDayService;
import team.project.TviyHub.event.EventRepository;
import team.project.TviyHub.exception.BadRequestException;
import team.project.TviyHub.exception.ConflictException;
import team.project.TviyHub.exception.ForbiddenException;
import team.project.TviyHub.exception.ResourceNotFoundException;
import team.project.TviyHub.payment.Payment;
import team.project.TviyHub.subscription.Subscription;
import team.project.TviyHub.subscription.SubscriptionRepository;
import team.project.TviyHub.tariff.Tariff;
import team.project.TviyHub.tariff.TariffRepository;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;
import team.project.TviyHub.workspace.Workspace;
import team.project.TviyHub.workspace.WorkspaceRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {
    private static final LocalTime HUB_OPEN_TIME = LocalTime.of(9, 0);
    private static final LocalTime HUB_CLOSE_TIME = LocalTime.of(19, 0);
    private static final long MIN_HOURLY_BOOKING_HOURS = 1;
    private static final long MAX_HOURLY_BOOKING_HOURS = 4;
    private static final long DAILY_CANCELLATION_HOURS_BEFORE = 24;
    private static final long HOURLY_CANCELLATION_HOURS_BEFORE = 2;
    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kyiv");

    private final BookingRepository bookingRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TariffRepository tariffRepository;
    private final UserRepository userRepository;
    private final ClosedDayService closedDayService;
    private final SubscriptionRepository subscriptionRepository;
    private final EventRepository eventRepository;
    private final BookingMapper bookingMapper;
    private final EntityManager entityManager;

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
    public List<BookingResponseDto> createBooking(BookingRequestDto requestDto, Long userId) {

        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Workspace workspace = workspaceRepository.findByIdForUpdate(requestDto.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace with ID " + requestDto.workspaceId() + " not found"));

        if (workspace.getType() == Workspace.WorkspaceType.MEETING_ROOM && requestDto.bookingType() == Tariff.BookingType.DAILY) {
            throw new BadRequestException("The meeting room can be booked by the hour only.");
        }

        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isDaily = requestDto.bookingType() == Tariff.BookingType.DAILY;

        List<LocalDate> validDates = requestDto.bookingDates().stream()
                .distinct()
                .filter(date -> {
                    if (eventRepository.existsByEventDate(date)) {
                        throw new ConflictException("The hub is fully rented for an event on " + date);
                    }
                    if (closedDayService.isDateClosed(date)) {
                        throw new ConflictException("The hub is closed on " + date + " (holiday, repair or special event)");
                    }
                    DayOfWeek dayOfWeek = date.getDayOfWeek();
                    if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                        throw new BadRequestException("The hub is closed on weekends (" + date + "). Booking is unavailable.");
                    }
                    return true;
                })
                .toList();

        LocalTime start;
        LocalTime end;

        if (!isDaily) {
            start = requestDto.startTime();
            end = requestDto.endTime();

            if (start == null || end == null || !end.isAfter(start)) {
                throw new BadRequestException("Invalid time slot: end time must be after start time");
            }

            if (start.getMinute() != 0 || end.getMinute() != 0) {
                throw new BadRequestException("Hourly booking is restricted to fixed 1-hour slots. Minutes must be 00.");
            }

            if (!isAdmin) {
                if (start.isBefore(HUB_OPEN_TIME) || end.isAfter(HUB_CLOSE_TIME)) {
                    throw new BadRequestException(
                            String.format("Clients can only book between %s and %s", HUB_OPEN_TIME, HUB_CLOSE_TIME)
                    );
                }
            }

            long hours = Duration.between(start, end).toHours();
            if (hours < MIN_HOURLY_BOOKING_HOURS || hours > MAX_HOURLY_BOOKING_HOURS) {
                throw new BadRequestException(
                        String.format("Hourly booking must be between %d and %d hours",
                        MIN_HOURLY_BOOKING_HOURS, MAX_HOURLY_BOOKING_HOURS)
                );
            }
        } else {
            start = HUB_OPEN_TIME;
            end = HUB_CLOSE_TIME;
        }

        List<Booking> bookingsToSave = new ArrayList<>();
        for (LocalDate date : validDates) {
            if (workspace.getType() == Workspace.WorkspaceType.DESK) {
                List<Subscription> activeSubs = subscriptionRepository.findOverlappingSubscriptions(
                        workspace.getId(),
                        date
                );
                if (!activeSubs.isEmpty()) {
                    throw new ConflictException("This desk is occupied by an active subscription for this date");
                }
            }

            List<Booking> activeBookings = bookingRepository.findActiveBookingsByWorkspaceAndDate(
                    workspace.getId(),
                    date
            );

            for (Booking existing : activeBookings) {
                if (existing.getBookingType() == Tariff.BookingType.DAILY) {
                    throw new ConflictException("Workspace is already booked for the whole day (DAILY)");
                }
                if (isDaily) {
                    throw new ConflictException("Cannot book for the whole day. Hourly bookings already exist");
                }
                if (start.isBefore(existing.getEndTime()) && end.isAfter(existing.getStartTime())) {
                    throw new ConflictException("Selected time slot overlaps with an existing booking");
                }
            }

            BookingRequestDto singleDayDto = new BookingRequestDto(
                    requestDto.workspaceId(),
                    requestDto.bookingType(),
                    List.of(date),
                    start,
                    end
            );

            BigDecimal totalPrice = calculateEstimatedPrice(singleDayDto);

            Payment initialPayment = Payment.builder()
                    .status(Payment.PaymentStatus.UNPAID)
                    .amount(totalPrice)
                    .paymentMethod(Payment.PaymentMethod.CASH)
                    .build();

            Booking booking = bookingMapper.toEntity(singleDayDto, currentUser, workspace);
            booking.setBookingDate(date);
            booking.setStartTime(start);
            booking.setEndTime(end);
            booking.setTotalPrice(totalPrice);
            booking.setStatus(Booking.BookingStatus.PENDING_PAYMENT);
            booking.setPayment(initialPayment);

            bookingsToSave.add(booking);
        }
        List<Booking> savedBookings = bookingRepository.saveAll(bookingsToSave);

        entityManager.flush();

        return savedBookings.stream()
                .map(bookingMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceAvailabilityDto> checkWorkspaceAvailability(LocalDate date, LocalTime startTime, LocalTime endTime) {
        if (closedDayService.isDateClosed(date) || eventRepository.existsByEventDate(date)) {
            return workspaceRepository.findAll().stream()
                    .map(w -> new WorkspaceAvailabilityDto(w.getId(), w.getName(), w.getType(), false))
                    .toList();
        }

        LocalTime effectiveStart = (startTime != null) ? startTime : HUB_OPEN_TIME;
        LocalTime effectiveEnd = (endTime != null) ? endTime : HUB_CLOSE_TIME;

        if (!effectiveEnd.isAfter(effectiveStart)) {
            throw new BadRequestException("Invalid time slot: end time must be after start time");
        }
        if (effectiveStart.isBefore(HUB_OPEN_TIME) || effectiveEnd.isAfter(HUB_CLOSE_TIME)) {
            return workspaceRepository.findAll().stream()
                    .map(w -> new WorkspaceAvailabilityDto(w.getId(), w.getName(), w.getType(), false))
                    .toList();
        }

        List<Workspace> allWorkspaces = workspaceRepository.findAll();
        List<Booking> activeBookings = bookingRepository.findByBookingDateAndStatusIn(date,
                List.of(Booking.BookingStatus.CONFIRMED, Booking.BookingStatus.PENDING_PAYMENT));

        return allWorkspaces.stream().map(workspace -> {
            boolean isAvailable = true;

            if (workspace.getType() == Workspace.WorkspaceType.DESK) {
                List<Subscription> activeSubs = subscriptionRepository.findOverlappingSubscriptionsForRead(workspace.getId(), date);
                if (!activeSubs.isEmpty()) {
                    isAvailable = false;
                }
            }

            if (isAvailable) {
                for (Booking booking : activeBookings) {
                    if (booking.getWorkspace() != null && booking.getWorkspace().getId().equals(workspace.getId())) {
                        if (booking.getBookingType() == Tariff.BookingType.DAILY) {
                            isAvailable = false;
                            break;
                        }

                        LocalTime existingStart = booking.getStartTime() != null ? booking.getStartTime() : HUB_OPEN_TIME;
                        LocalTime existingEnd = booking.getEndTime() != null ? booking.getEndTime() : HUB_CLOSE_TIME;

                        if (effectiveStart.isBefore(existingEnd) && effectiveEnd.isAfter(existingStart)) {
                            isAvailable = false;
                            break;
                        }
                    }
                }
            }

            return new WorkspaceAvailabilityDto(
                    workspace.getId(),
                    workspace.getName(),
                    workspace.getType(),
                    isAvailable
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateEstimatedPrice(BookingRequestDto requestDto) {
        Workspace workspace = workspaceRepository.findById(requestDto.workspaceId())
                .filter(w -> !w.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace with ID " + requestDto.workspaceId() + " not found"));

        if (workspace.getType() == Workspace.WorkspaceType.MEETING_ROOM && requestDto.bookingType() == Tariff.BookingType.DAILY) {
            throw new BadRequestException("The meeting room can be booked by the hour only.");
        }

        boolean isDaily = requestDto.bookingType() == Tariff.BookingType.DAILY;
        long hours = 0;

        if (!isDaily) {
            LocalTime start = requestDto.startTime();
            LocalTime end = requestDto.endTime();

            if (start == null || end == null || !end.isAfter(start)) {
                throw new BadRequestException("Invalid time slot: end time must be after start time");
            }

            if (start.getMinute() != 0 || end.getMinute() != 0) {
                throw new BadRequestException("Hourly booking is restricted to fixed 1-hour slots. Minutes must be 00.");
            }

            hours = Duration.between(start, end).toHours();
            if (hours < MIN_HOURLY_BOOKING_HOURS || hours > MAX_HOURLY_BOOKING_HOURS) {
                throw new BadRequestException(String.format("Hourly booking must be between %d and %d hours",
                        MIN_HOURLY_BOOKING_HOURS, MAX_HOURLY_BOOKING_HOURS)
                );
            }
        }

        Tariff tariff = tariffRepository.findByWorkspaceTypeAndBookingType(workspace.getType(), requestDto.bookingType())
                .orElseThrow(() -> new ResourceNotFoundException("Tariff not found for this resource and booking type"));

        return isDaily
                ? tariff.getPrice()
                : tariff.getPrice().multiply(BigDecimal.valueOf(hours));
    }

    @Transactional
    public BookingResponseDto cancelBooking(Long bookingId, User currentUser) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking with ID " + bookingId + " not found"));

        if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            throw new BadRequestException("Booking is already cancelled");
        }

        LocalDateTime nowInKyiv = LocalDateTime.now(KYIV_ZONE);
        LocalTime startTime = booking.getStartTime() != null ? booking.getStartTime() : HUB_OPEN_TIME;
        LocalDateTime bookingStart = LocalDateTime.of(booking.getBookingDate(), startTime);

        if (!nowInKyiv.isBefore(bookingStart)) {
            throw new BadRequestException("Cannot cancel a booking that has already started or passed");
        }

        if (currentUser.getRole() != Role.ADMIN) {
            if (!booking.getUser().getId().equals(currentUser.getId())) {
                throw new ForbiddenException("You can only cancel your own bookings");
            }

            long requiredHoursBefore = (booking.getBookingType() == Tariff.BookingType.DAILY)
                    ? DAILY_CANCELLATION_HOURS_BEFORE : HOURLY_CANCELLATION_HOURS_BEFORE;

            if (nowInKyiv.plusHours(requiredHoursBefore).isAfter(bookingStart)) {
                throw new BadRequestException(String.format("%s bookings can only be cancelled at least %d hours before the start time",
                        booking.getBookingType(), requiredHoursBefore));
            }
        }

        booking.setStatus(Booking.BookingStatus.CANCELLED);

        if (booking.getPayment() != null && Payment.PaymentStatus.PAID.equals(booking.getPayment().getStatus())) {
            booking.getPayment().setStatus(Payment.PaymentStatus.REFUNDED);
        }

        Booking saved = bookingRepository.save(booking);
        return bookingMapper.toDto(saved);
    }
}
