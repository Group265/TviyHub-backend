package team.project.TviyHub.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import team.project.TviyHub.booking.Booking;
import team.project.TviyHub.booking.BookingRepository;
import team.project.TviyHub.closedday.ClosedDayService;
import team.project.TviyHub.exception.BadRequestException;
import team.project.TviyHub.exception.ConflictException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Rules for when an event can take place. Only reads the booking and closed-day data of other
 * modules, it never changes them.
 */
@Component
@RequiredArgsConstructor
public class EventScheduling {

	private final EventRepository eventRepository;
	private final BookingRepository bookingRepository;
	private final ClosedDayService closedDayService;
	private final Clock clock;

	public LocalDate today() {
		return LocalDate.now(clock);
	}

	/** The date must be a Saturday or Sunday that has not passed, and the time range must be valid. */
	public void assertValidSlot(LocalDate date, LocalTime start, LocalTime end) {
		DayOfWeek day = date.getDayOfWeek();
		if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
			throw new BadRequestException("Events can only take place on weekends (Saturday or Sunday)");
		}
		if (date.isBefore(today())) {
			throw new BadRequestException("The event date must not be in the past");
		}
		if (!end.isAfter(start)) {
			throw new BadRequestException("The end time must be after the start time");
		}
	}

	/**
	 * The whole hub is rented for an event, so the day must have no other event, must not be marked
	 * as closed and must have no confirmed bookings.
	 *
	 * @param excludeEventId an event to ignore (the one being edited), or null
	 */
	public void assertDateAvailable(LocalDate date, Long excludeEventId) {
		boolean taken = excludeEventId == null
				? eventRepository.existsByEventDate(date)
				: eventRepository.existsByEventDateAndIdNot(date, excludeEventId);
		if (taken) {
			throw new ConflictException("Another event is already planned for " + date);
		}
		if (closedDayService.isDateClosed(date)) {
			throw new ConflictException("The hub is closed on " + date);
		}
		if (!bookingRepository.findByBookingDateAndStatus(date, Booking.BookingStatus.CONFIRMED).isEmpty()) {
			throw new ConflictException("There are confirmed bookings on " + date);
		}
	}
}
