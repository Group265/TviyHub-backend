package team.project.TviyHub.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
		@Schema(example = "5") Long id,
		@Schema(description = "ATTEND: take part in an event; HOST: propose a new event") EventRequestType type,
		@Schema(description = "NEW until an admin approves or rejects the request") EventRequestStatus status,
		@Schema(description = "Author of the request", example = "3") Long userId,
		@Schema(example = "Ivan Petrenko") String userName,
		@Schema(description = "ATTEND: the event; HOST: filled once the request is approved", example = "1") Long eventId,
		@Schema(example = "Weekend Hackathon") String eventTitle,
		@Schema(example = "2026-11-21") LocalDate eventDate,
		@Schema(description = "Proposed start time (HOST requests)", example = "10:00") LocalTime startTime,
		@Schema(description = "Proposed end time (HOST requests)", example = "18:00") LocalTime endTime,
		@Schema(description = "Proposed seats (HOST requests)", example = "30") Integer capacity,
		@Schema(description = "Proposed description (HOST requests)", example = "A one-day hackathon") String description,
		@Schema(description = "Message from the author", example = "I would like to attend") String comment,
		@Schema(description = "Reason given when the request was rejected", example = "The hub is closed for repairs")
		String reviewComment,
		@Schema(example = "2026-11-02T09:15:30Z") Instant createdAt,
		@Schema(description = "When an admin reviewed the request; null while NEW", example = "2026-11-03T10:00:00Z")
		Instant reviewedAt
) {

	public static EventRequestResponse from(EventRequest r, String userName, String eventTitle, LocalDate eventDate) {
		return new EventRequestResponse(r.getId(), r.getType(), r.getStatus(), r.getUserId(), userName,
				r.getEventId(), eventTitle, eventDate, r.getStartTime(), r.getEndTime(), r.getCapacity(),
				r.getDescription(), r.getComment(), r.getReviewComment(), r.getCreatedAt(), r.getReviewedAt());
	}
}
