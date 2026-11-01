package team.project.TviyHub.booking;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.workspace.id = :workspaceId " +
            "AND b.bookingDate = :date AND b.status = 'CONFIRMED'")
    List<Booking> findActiveBookingsByWorkspaceAndDate(
            @Param("workspaceId") Long workspaceId,
            @Param("date")LocalDate date
    );

    @Query("SELECT b FROM Booking b WHERE b.bookingDate = :bookingDate AND b.status = :status")
    List<Booking> findByBookingDateAndStatus(
            @Param("bookingDate") LocalDate bookingDate,
            @Param("status") Booking.BookingStatus status
    );

    List<Booking> findByUserId(Long userId);
}
