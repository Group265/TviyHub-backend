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
import team.project.TviyHub.exception.ConflictException;
import team.project.TviyHub.subscription.dto.SubscriptionRequest;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Slf4j
@SpringBootTest
@Import(TestcontainersConfig.class)
public class SubscriptionServiceConcurrencyTest {

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

            testUser1 = createUser("Subscriber One", "+380993333333", "sub1@tviyhub.com");
            testUser2 = createUser("Subscriber Two", "+380994444444", "sub2@tviyhub.com");

            entityManager.persist(testUser1);
            entityManager.persist(testUser2);
            return null;
        });
        successCount.set(0);
        conflictCount.set(0);
    }

    @Test
    void shouldPreventDoubleSubscriptionWhenConcurrentRequestsOccur() throws InterruptedException {
        Long targetWorkspaceId = 1L;
        LocalDate targetStartDate = LocalDate.now().plusDays(1);

        SubscriptionRequest requestDto = new SubscriptionRequest(
                targetWorkspaceId,
                targetStartDate
        );

        int numberOfThreads = 2;

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        executorService.submit(() -> executeSubscriptionTask(requestDto, testUser1.getId(), latch, finishLatch));
        executorService.submit(() -> executeSubscriptionTask(requestDto, testUser2.getId(), latch, finishLatch));

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
        user.setPasswordHash("$2a$10$xyzSubscription");
        user.setRole(Role.USER);
        user.setCreatedAt(Instant.now());
        user.setBlocked(false);
        user.setEmailVerified(true);
        return user;
    }

    private void executeSubscriptionTask(SubscriptionRequest dto, Long userId, CountDownLatch startLatch, CountDownLatch finishLatch) {
        try {
            startLatch.await();
            subscriptionService.createSubscription(userId, dto);
            successCount.incrementAndGet();
        } catch (ConflictException | PessimisticLockingFailureException e) {
            conflictCount.incrementAndGet();
        } catch (Exception e) {
            log.error("Unexpected error during concurrent subscription creation for user id: {}", userId, e);
        } finally {
            finishLatch.countDown();
        }
    }
}
