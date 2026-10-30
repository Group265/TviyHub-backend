package team.project.TviyHub.booking;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("SELECT b FROM Booking b WHERE b.workspace.id = :workspaceId " +
            "AND b.bookingDate = :date AND b.status = 'CONFIRMED'")
    List<Booking> findActiveBookingsByWorkspaceAndDate(
            @Param("workspaceId") Long workspaceId,
            @Param("date")LocalDate date
    );

    List<Booking> findByBookingDateAndStatus(LocalDate bookingDate, Booking.BookingStatus status);

    List<Booking> findByUserId(Long userId);
}
