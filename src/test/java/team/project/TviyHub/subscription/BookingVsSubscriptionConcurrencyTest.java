package team.project.TviyHub.subscription;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.booking.BookingService;
import team.project.TviyHub.booking.dto.BookingRequestDto;
import team.project.TviyHub.exception.ConflictException;
import team.project.TviyHub.subscription.dto.SubscriptionRequest;
import team.project.TviyHub.tariff.Tariff;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Slf4j
@SpringBootTest
@Import(TestcontainersConfig.class)
class BookingVsSubscriptionConcurrencyTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User testUser1;
    private User testUser2;
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger conflictCount = new AtomicInteger(0);

    @BeforeEach
    void setup() {
        transactionTemplate.execute(status -> {
            entityManager.createQuery("DELETE FROM Subscription").executeUpdate();
            entityManager.createQuery("DELETE FROM Booking").executeUpdate();
            entityManager.createQuery("DELETE FROM User").executeUpdate();

            testUser1 = createUser("Client Hourly", "+380995555555", "hourly@tviyhub.com");
            testUser2 = createUser("Client Subscription", "+380996666666", "sub_client@tviyhub.com");

            entityManager.persist(testUser1);
            entityManager.persist(testUser2);
            return null;
        });
        successCount.set(0);
        conflictCount.set(0);
    }

    @Test
    void shouldPreventRaceConditionBetweenBookingAndSubscription() throws InterruptedException {
        Long targetWorkspaceId = 1L;
        LocalDate targetDate = nextWeekday(LocalDate.now().plusDays(5));

        BookingRequestDto bookingRequest = new BookingRequestDto(
                targetWorkspaceId,
                Tariff.BookingType.HOURLY,
                List.of(targetDate),
                LocalTime.of(10, 0),
                LocalTime.of(12, 0)
        );

        SubscriptionRequest subscriptionRequest = new SubscriptionRequest(
                targetWorkspaceId,
                targetDate
        );

        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        executorService.submit(() -> {
            try {
                startLatch.await();
                bookingService.createBooking(bookingRequest, testUser1);
                successCount.incrementAndGet();
            } catch (ConflictException | PessimisticLockingFailureException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                log.error("Unexpected error during concurrent booking task", e);
            } finally {
                finishLatch.countDown();
            }
        });

        executorService.submit(() -> {
            try {
                startLatch.await();
                subscriptionService.createSubscription(testUser2.getId(), subscriptionRequest);
                successCount.incrementAndGet();
            } catch (ConflictException | PessimisticLockingFailureException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                log.error("Unexpected error during concurrent subscription task", e);
            } finally {
                finishLatch.countDown();
            }
        });

        startLatch.countDown();
        finishLatch.await();
        executorService.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }

    private User createUser(String name, String phone, String email) {
        User user = new User();
        user.setName(name);
        user.setPhone(phone);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$xyzCombinedTest");
        user.setRole(Role.USER);
        user.setCreatedAt(Instant.now());
        user.setBlocked(false);
        user.setEmailVerified(true);
        return user;
    }

    private static LocalDate nextWeekday(LocalDate date) {
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }
}
