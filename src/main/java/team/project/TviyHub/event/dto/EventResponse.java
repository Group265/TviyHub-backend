package team.project.TviyHub.event.dto;

import team.project.TviyHub.event.Event;

import java.time.LocalDate;
import java.time.LocalTime;

/** Event card. {@code capacity} and {@code freeSeats} are null when the number of seats is unlimited. */
public record EventResponse(
		Long id,
		String title,
		String description,
		LocalDate date,
		LocalTime startTime,
		LocalTime endTime,
		Long hostUserId,
		String hostName,
		Integer capacity,
		long approvedParticipants,
		Integer freeSeats,
		boolean past
) {

	public static EventResponse from(Event event, String hostName, long approvedParticipants, boolean past) {
		Integer free = event.getCapacity() == null
				? null
				: (int) Math.max(0, event.getCapacity() - approvedParticipants);
		return new EventResponse(event.getId(), event.getTitle(), event.getDescription(), event.getEventDate(),
				event.getStartTime(), event.getEndTime(), event.getHostUserId(), hostName,
				event.getCapacity(), approvedParticipants, free, past);
	}
}
