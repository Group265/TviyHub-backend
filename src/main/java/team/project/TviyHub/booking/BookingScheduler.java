package team.project.TviyHub.booking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.payment.Payment;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingScheduler {
    private static final LocalTime DEFAULT_START_TIME = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_END_TIME = LocalTime.of(19, 0);
    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kyiv");

    private final BookingRepository bookingRepository;

    @Scheduled(cron = "0 1 * * * *")
    @Transactional
    public void cancelUnpaidBookings() {
        LocalDateTime nowInKyiv = LocalDateTime.now(KYIV_ZONE);
        log.info("Checking unpaid bookings at {}...", nowInKyiv);

        List<Booking> unpaidBookings = bookingRepository
                .findByStatusAndPaymentStatus(
                        List.of(Booking.BookingStatus.CONFIRMED, Booking.BookingStatus.PENDING_PAYMENT),
                        Payment.PaymentStatus.UNPAID
                );

        int cancelledCount = 0;
        for (Booking booking : unpaidBookings) {
            LocalTime start = booking.getStartTime() != null ? booking.getStartTime() : DEFAULT_START_TIME;
            LocalDateTime bookingStart = LocalDateTime.of(booking.getBookingDate(), start);
            LocalDateTime plus24Hours = booking.getCreatedAt().plusHours(24);

            LocalDateTime paymentDeadline = plus24Hours.isBefore(bookingStart) ? plus24Hours : bookingStart;

            if (!nowInKyiv.isBefore(paymentDeadline)) {
                booking.setStatus(Booking.BookingStatus.CANCELLED);
                cancelledCount++;
                log.info("Booking ID {} auto-cancelled due to non-payment (deadline was {}).",
                        booking.getId(), paymentDeadline);
            }
        }
        log.info("Auto-cancelled unpaid bookings count: {}", cancelledCount);
    }

    @Scheduled(cron = "0 2 * * * *")
    @Transactional
    public void completeFinishedBookings() {
        LocalDateTime nowInKyiv = LocalDateTime.now(KYIV_ZONE);
        log.info("Checking finished bookings to mark as COMPLETED at {}...", nowInKyiv);

        List<Booking> confirmedBookings = bookingRepository.findByStatusAndPaymentStatus(
                List.of(Booking.BookingStatus.CONFIRMED),
                Payment.PaymentStatus.PAID
        );

        int completedCount = 0;
        for (Booking booking : confirmedBookings) {
            LocalTime end = booking.getEndTime() != null ? booking.getEndTime() : DEFAULT_END_TIME;
            LocalDateTime bookingEnd = LocalDateTime.of(booking.getBookingDate(), end);

            if (!nowInKyiv.isBefore(bookingEnd)) {
                booking.setStatus(Booking.BookingStatus.COMPLETED);
                completedCount++;
                log.info("Booking ID {} marked as COMPLETED (ended at {}).",
                        booking.getId(), bookingEnd);
            }
        }
        log.info("Auto-completed bookings count: {}", completedCount);
    }
}
