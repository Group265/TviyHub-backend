package team.project.TviyHub.auth.google;

/** What we need from a verified Google ID token. */
public record GoogleProfile(String subject, String email, boolean emailVerified, String name) {
}
