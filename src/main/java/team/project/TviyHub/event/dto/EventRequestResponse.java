package team.project.TviyHub.event.dto;

import team.project.TviyHub.event.EventRequest;
import team.project.TviyHub.event.EventRequestStatus;
import team.project.TviyHub.event.EventRequestType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A request as seen by its author and by admins. The proposed-event fields are filled for HOST
 * requests; eventId points to the event (existing for ATTEND, created on approval for HOST).
 */
public record EventRequestResponse(
		Long id,
		EventRequestType type,
		EventRequestStatus status,
		Long userId,
		String userName,
		Long eventId,
		String eventTitle,
		LocalDate eventDate,
		LocalTime startTime,
		LocalTime endTime,
		Integer capacity,
		String description,
		String comment,
		String reviewComment,
		Instant createdAt,
		Instant reviewedAt
) {

	public static EventRequestResponse from(EventRequest r, String userName, String eventTitle, LocalDate eventDate) {
		return new EventRequestResponse(r.getId(), r.getType(), r.getStatus(), r.getUserId(), userName,
				r.getEventId(), eventTitle, eventDate, r.getStartTime(), r.getEndTime(), r.getCapacity(),
				r.getDescription(), r.getComment(), r.getReviewComment(), r.getCreatedAt(), r.getReviewedAt());
	}
}
