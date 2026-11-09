package team.project.TviyHub.user.bookings;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.user.dto.PageResponse;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class BookingHistoryService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final ZoneId ZONE = ZoneId.of("Europe/Kyiv");

	private final MyBookingRepository repository;
	private final Clock clock;

	public BookingHistoryService(MyBookingRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/** Upcoming: nearest first. All and past: newest first. Within a day: by start time. */
	@Transactional(readOnly = true)
	public PageResponse<BookingHistoryItem> list(Long userId, BookingHistoryScope scope, int page, int size) {
		LocalDate today = LocalDate.now(clock.withZone(ZONE));
		Sort sort = scope == BookingHistoryScope.UPCOMING
				? Sort.by(Sort.Order.asc("b.bookingDate"), Sort.Order.asc("b.startTime"), Sort.Order.asc("b.id"))
				: Sort.by(Sort.Order.desc("b.bookingDate"), Sort.Order.desc("b.startTime"), Sort.Order.desc("b.id"));
		PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);

		Page<BookingHistoryItem> result = switch (scope) {
			case UPCOMING -> repository.findUpcomingOf(userId, today, pageRequest);
			case PAST -> repository.findPastOf(userId, today, pageRequest);
			case ALL -> repository.findAllOf(userId, pageRequest);
		};
		return PageResponse.from(result);
	}
}
