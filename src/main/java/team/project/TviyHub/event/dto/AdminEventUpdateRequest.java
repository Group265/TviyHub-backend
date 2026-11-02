package team.project.TviyHub.event.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/** Partial update: a null field is left unchanged. */
public record AdminEventUpdateRequest(
		@Size(max = 150) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String title,
		@Size(max = 5000) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String description,
		LocalDate date,
		LocalTime startTime,
		LocalTime endTime,
		@Min(1) @Max(10000) Integer capacity
) {
}
