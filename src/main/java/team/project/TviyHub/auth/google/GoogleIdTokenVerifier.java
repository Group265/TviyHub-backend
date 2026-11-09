package team.project.TviyHub.auth.google;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import team.project.TviyHub.exception.ServiceUnavailableException;
import team.project.TviyHub.exception.UnauthorizedException;

import java.util.List;

/**
 * Checks a Google ID token: signature (Google public keys), expiry, issuer and that it was issued for our client id.
 * Deliberately not a JwtDecoder bean, so it does not replace the decoder for our own access tokens.
 */
@Component
public class GoogleIdTokenVerifier {

	private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
	private static final List<String> ISSUERS = List.of("accounts.google.com", "https://accounts.google.com");

	private final String clientId;
	private volatile JwtDecoder decoder;

	public GoogleIdTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
		this.clientId = clientId == null ? "" : clientId.trim();
	}

	public GoogleProfile verify(String idToken) {
		if (clientId.isEmpty()) {
			throw new ServiceUnavailableException("Google sign-in is not configured");
		}
		Jwt jwt;
		try {
			jwt = decoder().decode(idToken);
		} catch (JwtException e) {
			throw new UnauthorizedException("Invalid Google token");
		}
		String email = jwt.getClaimAsString("email");
		if (jwt.getSubject() == null || email == null || email.isBlank()) {
			throw new UnauthorizedException("Invalid Google token");
		}
		Object verified = jwt.getClaims().get("email_verified");
		boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
		return new GoogleProfile(jwt.getSubject(), email, emailVerified, jwt.getClaimAsString("name"));
	}

	private JwtDecoder decoder() {
		JwtDecoder local = decoder;
		if (local == null) {
			NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(JWKS_URI)
					.jwsAlgorithm(SignatureAlgorithm.RS256).build();
			OAuth2TokenValidator<Jwt> issuer = jwt -> ISSUERS.contains(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())
					? OAuth2TokenValidatorResult.success()
					: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong issuer", null));
			OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
					? OAuth2TokenValidatorResult.success()
					: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null));
			nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience));
			decoder = local = nimbus;
		}
		return local;
	}
}
