package team.project.TviyHub.booking;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.payment.Payment;
import team.project.TviyHub.tariff.Tariff;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;
import team.project.TviyHub.workspace.Workspace;
import team.project.TviyHub.workspace.WorkspaceRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class BookingSchedulerTest {

    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kyiv");

    @Autowired
    private BookingScheduler bookingScheduler;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private Workspace testWorkspace;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .name("Test Client")
                .email("client@hub.com")
                .passwordHash("hash")
                .phone("+380501112233")
                .role(Role.USER)
                .build());

        testWorkspace = workspaceRepository.save(Workspace.builder()
                .name("Робоче місце №1")
                .type(Workspace.WorkspaceType.DESK)
                .build());
    }

    @Test
    void shouldCancelUnpaidBookingOlderThan24Hours() {
        LocalDateTime nowInKyiv = LocalDateTime.now(KYIV_ZONE);
        Booking booking = createAndSaveBooking(
                nowInKyiv.toLocalDate().plusDays(5),
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                Booking.BookingStatus.PENDING_PAYMENT,
                Payment.PaymentStatus.UNPAID,
                nowInKyiv.minusHours(25)
        );

        bookingScheduler.cancelUnpaidBookings();

        Booking updated = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Booking.BookingStatus.CANCELLED);
    }

    @Test
    void shouldCancelUnpaidBookingWhenStartTimeReachedWithin24Hours() {
        LocalDateTime nowInKyiv = LocalDateTime.now(KYIV_ZONE);
        LocalDateTime startedOneHourAgo = nowInKyiv.minusHours(1);

        Booking booking = createAndSaveBooking(
                startedOneHourAgo.toLocalDate(),
                startedOneHourAgo.toLocalTime().withMinute(0).withSecond(0).withNano(0),
                startedOneHourAgo.plusHours(2).toLocalTime().withMinute(0).withSecond(0).withNano(0),
                Booking.BookingStatus.PENDING_PAYMENT,
                Payment.PaymentStatus.UNPAID,
                nowInKyiv.minusHours(2)
        );

        bookingScheduler.cancelUnpaidBookings();

        Booking updated = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Booking.BookingStatus.CANCELLED);
    }

    @Test
    void shouldKeepFreshUnpaidBookingActiveBeforeDeadline() {
        LocalDateTime nowInKyiv = LocalDateTime.now(KYIV_ZONE);
        Booking booking = createAndSaveBooking(
                nowInKyiv.toLocalDate().plusDays(1),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                Booking.BookingStatus.PENDING_PAYMENT,
                Payment.PaymentStatus.UNPAID,
                nowInKyiv.minusHours(1)
        );

        bookingScheduler.cancelUnpaidBookings();

        Booking updated = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Booking.BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldMarkFinishedPaidBookingAsCompleted() {
        LocalDateTime nowInKyiv = LocalDateTime.now(KYIV_ZONE);
        Booking booking = createAndSaveBooking(
                nowInKyiv.toLocalDate().minusDays(1),
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                Booking.BookingStatus.CONFIRMED,
                Payment.PaymentStatus.PAID,
                nowInKyiv.minusDays(2)
        );

        bookingScheduler.completeFinishedBookings();

        Booking updated = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Booking.BookingStatus.COMPLETED);
    }

    private Booking createAndSaveBooking(
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            Booking.BookingStatus bookingStatus,
            Payment.PaymentStatus paymentStatus,
            LocalDateTime desiredCreatedAt
    ) {
        Payment payment = Payment.builder()
                .status(paymentStatus)
                .amount(new BigDecimal("300.00"))
                .paymentMethod(Payment.PaymentMethod.CASH)
                .build();

        Booking booking = Booking.builder()
                .user(testUser)
                .workspace(testWorkspace)
                .bookingType(Tariff.BookingType.HOURLY)
                .bookingDate(date)
                .startTime(startTime)
                .endTime(endTime)
                .totalPrice(new BigDecimal("300.00"))
                .status(bookingStatus)
                .payment(payment)
                .build();

        Booking saved = bookingRepository.saveAndFlush(booking);

        jdbcTemplate.update(
                "UPDATE bookings SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(desiredCreatedAt),
                saved.getId()
        );

        entityManager.clear();

        return saved;
    }
}
