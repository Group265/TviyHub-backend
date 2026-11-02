package team.project.TviyHub.event.dto;

import jakarta.validation.constraints.Size;

/** Optional explanation shown to the user when a request is rejected. */
public record ReviewEventRequest(@Size(max = 500) String reason) {
}
