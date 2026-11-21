package team.project.TviyHub.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {
    private static final LocalTime HUB_CLOSE_TIME = LocalTime.of(19, 0);

    private final SubscriptionRepository subscriptionRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cancelUnpaidSubscriptions() {
        log.info("Starting background process to clean up unpaid subscriptions...");

        LocalDateTime nowInKyiv = LocalDateTime.now(ZoneId.of("Europe/Kyiv"));

        List<Subscription> pendingSubscriptions = subscriptionRepository
                .findByStatus(Subscription.SubscriptionStatus.PENDING_PAYMENT);

        int cancelledCount = 0;
        for (Subscription sub : pendingSubscriptions) {
            LocalDateTime plus24Hours = sub.getCreatedAt().plusHours(24);
            LocalDateTime startDayClose = sub.getStartDate().atTime(HUB_CLOSE_TIME);
            LocalDateTime deadline = plus24Hours.isBefore(startDayClose) ? plus24Hours : startDayClose;

            if (!nowInKyiv.isBefore(deadline)) {
                sub.setStatus(Subscription.SubscriptionStatus.CANCELLED);
                cancelledCount++;
                log.info("Subscription ID {} automatically cancelled (deadline was {}). Workspace ID {} is now available.",
                        sub.getId(), deadline, sub.getWorkspace().getId());
            }
        }
        log.info("Cleanup process finished. Automatically cancelled subscriptions count: {}", cancelledCount);
    }

    @Scheduled(cron = "0 1 0 * * *")
    @Transactional
    public void expireCompletedSubscriptions() {
        log.info("Starting background process to expire completed subscriptions...");

        LocalDate todayInKyiv = LocalDate.now(ZoneId.of("Europe/Kyiv"));

        List<Subscription> outdatedSubscriptions = subscriptionRepository
                .findByStatusAndEndDateBefore(Subscription.SubscriptionStatus.ACTIVE, todayInKyiv);
        if (outdatedSubscriptions.isEmpty()) {
            log.info("No active subscriptions to expire today.");
            return;
        }

        for (Subscription sub : outdatedSubscriptions) {
            sub.setStatus(Subscription.SubscriptionStatus.EXPIRED);
            log.info("Subscription ID {} has reached its end date ({}) and automatically changed status ot EXPIRED.",
                    sub.getId(), sub.getEndDate());
        }
        log.info("Expiration process finished successfully. Automatically expired subscriptions count: {}",
                outdatedSubscriptions.size());
    }
}
