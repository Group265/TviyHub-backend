package team.project.TviyHub.user.bookings;

import team.project.TviyHub.exception.BadRequestException;

import java.util.Locale;

public enum BookingHistoryScope {
	ALL, UPCOMING, PAST;

	public static BookingHistoryScope parse(String value) {
		if (value == null || value.isBlank()) {
			return ALL;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new BadRequestException("scope must be one of: all, upcoming, past");
		}
	}
}
