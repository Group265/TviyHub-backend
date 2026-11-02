package team.project.TviyHub.event.dto;

import jakarta.validation.constraints.Size;

public record AttendEventRequest(@Size(max = 500) String comment) {
}
