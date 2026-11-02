package team.project.TviyHub.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.project.TviyHub.event.Event;

import java.time.LocalDate;
import java.time.LocalTime;

/** Event card. {@code capacity} and {@code freeSeats} are null when the number of seats is unlimited. */
public record EventResponse(
		@Schema(example = "1") Long id,
		@Schema(example = "Weekend Hackathon") String title,
		@Schema(example = "A one-day hackathon for students and startups") String description,
		@Schema(example = "2026-11-21") LocalDate date,
		@Schema(example = "10:00") LocalTime startTime,
		@Schema(example = "18:00") LocalTime endTime,
		@Schema(description = "Organizer user id; null if the event has no organizer", example = "2") Long hostUserId,
		@Schema(description = "Organizer name", example = "Olena Shevchenko") String hostName,
		@Schema(description = "Seats for participants; null means unlimited", example = "30") Integer capacity,
		@Schema(description = "Participants whose requests were approved", example = "12") long approvedParticipants,
		@Schema(description = "Seats left; null means unlimited", example = "18") Integer freeSeats,
		@Schema(description = "True if the event date has passed", example = "false") boolean past
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
