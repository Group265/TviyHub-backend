package team.project.TviyHub.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/** An admin creates an event directly, optionally naming an organizer (a user id). */
public record AdminEventRequest(
		@Schema(example = "Monthly Meetup")
		@NotBlank @Size(max = 150) String title,
		@Schema(example = "Open meetup for the hub community")
		@NotBlank @Size(max = 5000) String description,
		@Schema(description = "A free Saturday or Sunday that is not in the past", example = "2026-11-22")
		@NotNull LocalDate date,
		@Schema(example = "11:00") @NotNull LocalTime startTime,
		@Schema(description = "Must be after the start time", example = "15:00") @NotNull LocalTime endTime,
		@Schema(description = "Number of seats for participants; omit for unlimited", example = "50")
		@Min(1) @Max(10000) Integer capacity,
		@Schema(description = "Id of the organizer; omit if the event has no organizer", example = "2")
		Long hostUserId
) {
}
