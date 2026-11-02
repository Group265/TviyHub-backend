package team.project.TviyHub.booking;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.booking.dto.BookingRequestDto;
import team.project.TviyHub.exception.ConflictException;
import team.project.TviyHub.tariff.Tariff;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;

import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
class BookingServiceConcurrencyTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User testUser1;
    private User testUser2;

    @BeforeEach
    void setup() {
        transactionTemplate.execute(status -> {
            entityManager.createQuery("DELETE FROM Booking").executeUpdate();

            testUser1 = new User();
            testUser1.setName("User One");
            testUser1.setPhone("+380991111111");
            testUser1.setEmail("user1@tviyhub.com");
            testUser1.setPasswordHash("$2a$10$xyz");
            testUser1.setRole(Role.USER);
            testUser1.setCreatedAt(Instant.now());
            testUser1.setBlocked(false);
            testUser1.setEmailVerified(true);

            testUser2 = new User();
            testUser2.setName("User Two");
            testUser2.setPhone("+380992222222");
            testUser2.setEmail("user2@tviyhub.com");
            testUser2.setPasswordHash("$2a$10$xyz");
            testUser2.setRole(Role.USER);
            testUser2.setCreatedAt(Instant.now());
            testUser2.setBlocked(false);
            testUser2.setEmailVerified(true);

            entityManager.persist(testUser1);
            entityManager.persist(testUser2);
            return null;
        });
    }

    @Test
    void shouldPreventDoubleBookingWhenConcurrentRequestsOccur() throws InterruptedException {
        Long targetWorkspaceId = 1L;
        LocalDate targetDate = nextWeekday(LocalDate.now().plusDays(5));

        BookingRequestDto requestDto = new BookingRequestDto(
                targetWorkspaceId,
                Tariff.BookingType.HOURLY,
                targetDate,
                LocalTime.of(12, 0),
                LocalTime.of(14, 0)
        );

        int numberOfThreads = 2;

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        executorService.submit(() -> {
            try {
                latch.await();
                bookingService.createBooking(requestDto, testUser1);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (PessimisticLockingFailureException e) {
                conflictCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        executorService.submit(() -> {
            try {
                latch.await();
                bookingService.createBooking(requestDto, testUser2);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (PessimisticLockingFailureException e) {
                conflictCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        latch.countDown();
        finishLatch.await();
        executorService.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }

    /** Clients cannot book on weekends, so the test must not run on a Saturday or Sunday. */
    private static LocalDate nextWeekday(LocalDate date) {
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }
}
