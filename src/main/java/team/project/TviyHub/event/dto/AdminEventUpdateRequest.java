package team.project.TviyHub.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/** Partial update: a null field is left unchanged. */
public record AdminEventUpdateRequest(
		@Schema(description = "Omit to keep the current value", example = "Big Monthly Meetup")
		@Size(max = 150) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String title,
		@Schema(description = "Omit to keep the current value", example = "Open meetup with talks and networking")
		@Size(max = 5000) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String description,
		@Schema(description = "A new free Saturday or Sunday; omit to keep the current date", example = "2026-11-29")
		LocalDate date,
		@Schema(description = "Omit to keep the current value", example = "12:00") LocalTime startTime,
		@Schema(description = "Omit to keep the current value", example = "16:00") LocalTime endTime,
		@Schema(description = "Cannot be lower than the number of approved participants", example = "60")
		@Min(1) @Max(10000) Integer capacity
) {
}
