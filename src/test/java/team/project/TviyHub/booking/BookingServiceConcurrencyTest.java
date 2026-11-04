package team.project.TviyHub.booking;

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
import team.project.TviyHub.booking.dto.BookingRequestDto;
import team.project.TviyHub.exception.ConflictException;
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
class BookingServiceConcurrencyTest {

    @Autowired
    private BookingService bookingService;

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
            entityManager.createQuery("DELETE FROM Booking").executeUpdate();

            testUser1 = createUser("User One", "+380991111111", "user1@tviyhub.com");
            testUser2 = createUser("User Two", "+380992222222", "user2@tviyhub.com");

            entityManager.persist(testUser1);
            entityManager.persist(testUser2);
            return null;
        });
        successCount.set(0);
        conflictCount.set(0);
    }

    @Test
    void shouldPreventDoubleBookingWhenConcurrentRequestsOccur() throws InterruptedException {
        Long targetWorkspaceId = 1L;
        LocalDate targetDate = nextWeekday(LocalDate.now().plusDays(5));

        BookingRequestDto requestDto = new BookingRequestDto(
                targetWorkspaceId,
                Tariff.BookingType.HOURLY,
                List.of(targetDate),
                LocalTime.of(12, 0),
                LocalTime.of(14, 0)
        );

        int numberOfThreads = 2;

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        executorService.submit(() -> executeBookingTask(requestDto, testUser1, latch, finishLatch));
        executorService.submit(() -> executeBookingTask(requestDto, testUser2, latch, finishLatch));

        latch.countDown();
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
        user.setPasswordHash("$2a$10$xyz");
        user.setRole(Role.USER);
        user.setCreatedAt(Instant.now());
        user.setBlocked(false);
        user.setEmailVerified(true);
        return user;
    }

    private void executeBookingTask(BookingRequestDto dto, User user, CountDownLatch startLatch, CountDownLatch finishLatch) {
        try {
            startLatch.await();
            bookingService.createBooking(dto, user.getId());
            successCount.incrementAndGet();
        } catch (ConflictException | PessimisticLockingFailureException e) {
            conflictCount.incrementAndGet();
        } catch (Exception e) {
            log.error("Unexpected error during concurrent booking for user: {}", user.getEmail(), e);
        } finally {
            finishLatch.countDown();
        }
    }

    /** Clients cannot book on weekends, so the test must not run on a Saturday or Sunday. */
    private static LocalDate nextWeekday(LocalDate date) {
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }
}
