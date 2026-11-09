package team.project.TviyHub.user.bookings;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import team.project.TviyHub.booking.Booking;

import java.time.LocalDate;

/** Read-only queries for the booking history. Kept apart from the booking module's own repository. */
public interface MyBookingRepository extends Repository<Booking, Long> {

	String SELECT = """
			select new team.project.TviyHub.user.bookings.BookingHistoryItem(
				b.id, b.bookingDate, b.startTime, b.endTime, b.bookingType, b.status, b.totalPrice,
				w.id, w.name, w.type)
			from Booking b join b.workspace w
			where b.user.id = :userId
			""";
	String COUNT = "select count(b) from Booking b where b.user.id = :userId";

	@Query(value = SELECT, countQuery = COUNT)
	Page<BookingHistoryItem> findAllOf(@Param("userId") Long userId, Pageable pageable);

	@Query(value = SELECT + " and b.bookingDate >= :today",
			countQuery = COUNT + " and b.bookingDate >= :today")
	Page<BookingHistoryItem> findUpcomingOf(@Param("userId") Long userId, @Param("today") LocalDate today,
											Pageable pageable);

	@Query(value = SELECT + " and b.bookingDate < :today",
			countQuery = COUNT + " and b.bookingDate < :today")
	Page<BookingHistoryItem> findPastOf(@Param("userId") Long userId, @Param("today") LocalDate today,
										Pageable pageable);
}
