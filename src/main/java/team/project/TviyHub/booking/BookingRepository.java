package team.project.TviyHub.booking;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.project.TviyHub.payment.Payment;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM Booking b WHERE b.workspace.id = :workspaceId 
            AND b.bookingDate = :date AND b.status IN ('CONFIRMED', 'PENDING_PAYMENT')"""
    )
    List<Booking> findActiveBookingsByWorkspaceAndDate(
            @Param("workspaceId") Long workspaceId,
            @Param("date")LocalDate date
    );

    @Query("SELECT b FROM Booking b WHERE b.bookingDate = :bookingDate AND b.status IN :statuses")
    List<Booking> findByBookingDateAndStatusIn(
            @Param("bookingDate") LocalDate bookingDate,
            @Param("statuses") List<Booking.BookingStatus> statuses
    );

    List<Booking> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM Booking b 
            WHERE b.workspace.id = :workspaceId 
            AND b.bookingDate BETWEEN :startDate AND :endDate 
            AND b.status IN ('CONFIRMED', 'PENDING_PAYMENT')
    """)
    List<Booking> existsActiveBookingsInPeriod(
            @Param("workspaceId") Long workspaceId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.workspace.id = :workspaceId
            AND b.bookingDate >= :today
            AND b.status IN ('CONFIRMED', 'PENDING_PAYMENT')
    """)
    boolean existsFutureActiveBookings(
            @Param("workspaceId") Long workspaceId,
            @Param("today") LocalDate today
    );


    @Query("""
            SELECT b FROM Booking b JOIN FETCH b.payment p 
            WHERE b.status IN :statuses AND p.status = :paymentStatus""")
    List<Booking> findByStatusAndPaymentStatus(
            @Param("statuses") List<Booking.BookingStatus> status,
            @Param("paymentStatus") Payment.PaymentStatus paymentStatus
    );
}
