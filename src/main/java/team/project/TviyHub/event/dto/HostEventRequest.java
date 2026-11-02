package team.project.TviyHub.event.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/** A proposal to hold an event on a weekend day; the whole hub is rented for it. */
public record HostEventRequest(
		@NotBlank @Size(max = 150) String title,
		@NotBlank @Size(max = 5000) String description,
		@NotNull LocalDate date,
		@NotNull LocalTime startTime,
		@NotNull LocalTime endTime,
		@Min(1) @Max(10000) Integer capacity,
		@Size(max = 500) String comment
) {
}
