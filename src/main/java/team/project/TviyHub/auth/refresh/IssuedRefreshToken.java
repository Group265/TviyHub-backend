package team.project.TviyHub.auth.refresh;

import team.project.TviyHub.user.User;

/** A freshly issued refresh token (raw value, shown to the client once) and its owner. */
public record IssuedRefreshToken(User user, String token) {
}
