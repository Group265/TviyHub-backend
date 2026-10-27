package team.project.TviyHub.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {

    private final SubscriptionRepository subscriptionRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cancelUnpaidSubscriptions() {
        log.info("Starting background process to clean up unpaid subscriptions...");

        LocalDateTime thresholdTime = LocalDateTime.now().minusHours(24);

        List<Subscription> expiredSubscriptions = subscriptionRepository
                .findByStatusAndCreatedAtBefore(Subscription.SubscriptionStatus.PENDING_PAYMENT, thresholdTime);

        if (expiredSubscriptions.isEmpty()) {
            log.info("No unpaid subscriptions older than 24 hours found.");
            return;
        }

        for (Subscription sub : expiredSubscriptions) {
            sub.setStatus(Subscription.SubscriptionStatus.CANCELLED);
            log.info("Subscription ID {} automatically cancelled (payment timeout). Workspace ID {} is now available.",
                    sub.getId(), sub.getWorkspace().getId());
        }

        log.info("Cleanup process finished successfully. Automatically cancelled subscriptions count: {}", expiredSubscriptions.size());
    }
}
