package team.project.TviyHub.subscription;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
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

    @Query("""
        SELECT s FROM Subscription s 
        WHERE s.workspace.id = :workspaceId 
        AND :date BETWEEN s.startDate AND s.endDate
        AND s.status IN ('ACTIVE', 'PENDING_PAYMENT')
    """)
    List<Subscription> findOverlappingSubscriptionsForRead(
            @Param("workspaceId") Long workspaceId,
            @Param("date") LocalDate date
    );

    List<Subscription> findByStatusAndCreatedAtBefore(
            Subscription.SubscriptionStatus status,
            LocalDateTime dateTime
    );
}
