package team.project.TviyHub.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StripeSessionResponseDto(
   String sessionUrl,
   String sessionId
) {
    @JsonProperty("url")
    public String url() {
        return sessionUrl;
    }
}
