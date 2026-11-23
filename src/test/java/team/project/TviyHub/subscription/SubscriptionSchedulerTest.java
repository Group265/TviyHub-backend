package team.project.TviyHub.subscription;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.booking.BookingRepository;
import team.project.TviyHub.payment.Payment;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;
import team.project.TviyHub.workspace.Workspace;
import team.project.TviyHub.workspace.WorkspaceRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class SubscriptionSchedulerTest {

    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kyiv");

    @Autowired
    private SubscriptionScheduler subscriptionScheduler;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

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
    private Workspace testDesk;

    @BeforeEach
    void setUp() {
        // Bookings left by other test classes would block deleting their users
        bookingRepository.deleteAll();
        subscriptionRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .name("Test Subscriber")
                .email("sub@hub.com")
                .passwordHash("hash")
                .phone("+380509998877")
                .role(Role.USER)
                .build());

        testDesk = workspaceRepository.save(Workspace.builder()
                .name("Робоче місце №5")
                .type(Workspace.WorkspaceType.DESK)
                .build());
    }

    @Test
    void shouldCancelUnpaidSubscriptionOlderThan24Hours() {
        LocalDateTime nowInKyiv = LocalDateTime.now(KYIV_ZONE);
        Subscription sub = createAndSaveSubscription(
                nowInKyiv.toLocalDate().plusDays(5),
                nowInKyiv.toLocalDate().plusDays(34),
                Subscription.SubscriptionStatus.PENDING_PAYMENT,
                Payment.PaymentStatus.UNPAID,
                nowInKyiv.minusHours(25)
        );

        subscriptionScheduler.cancelUnpaidSubscriptions();

        Subscription updated = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Subscription.SubscriptionStatus.CANCELLED);
    }

    @Test
    void shouldCancelUnpaidSubscriptionWhenStartDayCloseTimePassedWithin24Hours() {
        LocalDateTime nowInKyiv = LocalDateTime.now(KYIV_ZONE);
        LocalDate yesterday = nowInKyiv.toLocalDate().minusDays(1);

        Subscription sub = createAndSaveSubscription(
                yesterday,
                yesterday.plusMonths(1).minusDays(1),
                Subscription.SubscriptionStatus.PENDING_PAYMENT,
                Payment.PaymentStatus.UNPAID,
                nowInKyiv.minusHours(10)
        );

        subscriptionScheduler.cancelUnpaidSubscriptions();

        Subscription updated = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Subscription.SubscriptionStatus.CANCELLED);
    }

    @Test
    void shouldKeepFreshUnpaidSubscriptionActiveBeforeDeadline() {
        LocalDateTime nowInKyiv = LocalDateTime.now(KYIV_ZONE);
        LocalDate tomorrow = nowInKyiv.toLocalDate().plusDays(1);

        Subscription sub = createAndSaveSubscription(
                tomorrow,
                tomorrow.plusMonths(1).minusDays(1),
                Subscription.SubscriptionStatus.PENDING_PAYMENT,
                Payment.PaymentStatus.UNPAID,
                nowInKyiv.minusHours(1)
        );

        subscriptionScheduler.cancelUnpaidSubscriptions();

        Subscription updated = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Subscription.SubscriptionStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldExpireCompletedActiveSubscriptionAfterEndDate() {
        LocalDate todayInKyiv = LocalDate.now(KYIV_ZONE);
        Subscription sub = createAndSaveSubscription(
                todayInKyiv.minusMonths(1),
                todayInKyiv.minusDays(1),
                Subscription.SubscriptionStatus.ACTIVE,
                Payment.PaymentStatus.PAID,
                LocalDateTime.now(KYIV_ZONE).minusMonths(1)
        );

        subscriptionScheduler.expireCompletedSubscriptions();

        Subscription updated = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Subscription.SubscriptionStatus.EXPIRED);
    }

    private Subscription createAndSaveSubscription(
            LocalDate startDate,
            LocalDate endDate,
            Subscription.SubscriptionStatus subStatus,
            Payment.PaymentStatus paymentStatus,
            LocalDateTime desiredCreatedAt
    ) {
        Payment payment = Payment.builder()
                .status(paymentStatus)
                .amount(new BigDecimal("3500.00"))
                .paymentMethod(Payment.PaymentMethod.CASH)
                .build();

        Subscription subscription = Subscription.builder()
                .user(testUser)
                .workspace(testDesk)
                .startDate(startDate)
                .endDate(endDate)
                .status(subStatus)
                .payment(payment)
                .build();

        payment.setSubscription(subscription);

        Subscription saved = subscriptionRepository.saveAndFlush(subscription);

        jdbcTemplate.update(
                "UPDATE subscriptions SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(desiredCreatedAt),
                saved.getId()
        );

        entityManager.clear();

        return saved;
    }
}
