package team.project.TviyHub.subscription;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @Query("""
        SELECT s FROM Subscription s 
        WHERE s.workspace.id = :workspaceId 
        AND :date BETWEEN s.startDate AND s.endDate
        AND s.status IN ('ACTIVE', 'PENDING_PAYMENT')
    """)
    List<Subscription> findOverlappingSubscriptions(
            @Param("workspaceId") Long workspaceId,
            @Param("date") LocalDate date
    );

    @Query("""
        SELECT s FROM Subscription s 
        WHERE s.workspace.id = :workspaceId 
          AND s.status IN ('ACTIVE', 'PENDING_PAYMENT')
          AND (:startDate <= s.endDate AND :endDate >= s.startDate)
    """)
    List<Subscription> findOverlappingSubscriptionsInPeriod(
            @Param("workspaceId") Long workspaceId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<Subscription> findByStatusAndCreatedAtBefore(
            Subscription.SubscriptionStatus status,
            LocalDateTime dateTime
    );
}
