package team.project.TviyHub.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import team.project.TviyHub.user.User;

import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {

	private final JwtEncoder jwtEncoder;
	private final Duration lifetime;

	public JwtService(JwtEncoder jwtEncoder, @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
		this.jwtEncoder = jwtEncoder;
		this.lifetime = Duration.ofMinutes(expirationMinutes);
	}

	public long getLifetimeSeconds() {
		return lifetime.toSeconds();
	}

	public String generateToken(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(String.valueOf(user.getId()))
				.issuedAt(now)
				.expiresAt(now.plus(lifetime))
				.claim("role", user.getRole().name())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
