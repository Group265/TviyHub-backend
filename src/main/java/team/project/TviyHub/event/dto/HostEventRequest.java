package team.project.TviyHub.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/** A proposal to hold an event on a weekend day; the whole hub is rented for it. */
public record HostEventRequest(
		@Schema(description = "Event title", example = "Weekend Hackathon")
		@NotBlank @Size(max = 150) String title,
		@Schema(description = "What the event is about", example = "A one-day hackathon for students and startups")
		@NotBlank @Size(max = 5000) String description,
		@Schema(description = "A free Saturday or Sunday that is not in the past", example = "2026-11-21")
		@NotNull LocalDate date,
		@Schema(example = "10:00") @NotNull LocalTime startTime,
		@Schema(description = "Must be after the start time", example = "18:00") @NotNull LocalTime endTime,
		@Schema(description = "Number of seats for participants; omit for unlimited", example = "30")
		@Min(1) @Max(10000) Integer capacity,
		@Schema(description = "Message for the admin who reviews the request", example = "We will bring our own equipment")
		@Size(max = 500) String comment
) {
}
