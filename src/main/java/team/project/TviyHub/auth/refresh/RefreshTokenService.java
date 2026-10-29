package team.project.TviyHub.auth.refresh;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.exception.ForbiddenException;
import team.project.TviyHub.exception.UnauthorizedException;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

	private static final String INVALID = "Invalid or expired refresh token";

	private final SecureRandom random = new SecureRandom();

	private final RefreshTokenRepository repository;
	private final UserRepository userRepository;
	private final Clock clock;
	private final Duration lifetime;

	public RefreshTokenService(RefreshTokenRepository repository, UserRepository userRepository, Clock clock,
							   @Value("${app.jwt.refresh-expiration-days}") long refreshExpirationDays) {
		this.repository = repository;
		this.userRepository = userRepository;
		this.clock = clock;
		this.lifetime = Duration.ofDays(refreshExpirationDays);
	}

	public long getLifetimeSeconds() {
		return lifetime.toSeconds();
	}

	/** Starts a new token family (a new login) and returns the raw token. */
	@Transactional
	public String issue(Long userId) {
		return create(userId, UUID.randomUUID().toString());
	}

	/**
	 * Exchanges a refresh token for a new one in the same family. The old token stops working.
	 * Presenting an already used token means it may have leaked, so the whole family is revoked;
	 * that revocation must survive the exception, hence noRollbackFor.
	 */
	@Transactional(noRollbackFor = UnauthorizedException.class)
	public IssuedRefreshToken rotate(String rawToken) {
		Instant now = clock.instant();
		RefreshToken current = repository.findByTokenHash(hash(rawToken))
				.orElseThrow(() -> new UnauthorizedException(INVALID));
		if (current.getRevokedAt() != null) {
			repository.revokeFamily(current.getFamilyId(), now);
			throw new UnauthorizedException(INVALID);
		}
		if (current.getExpiresAt().isBefore(now)) {
			throw new UnauthorizedException(INVALID);
		}
		User user = userRepository.findById(current.getUserId())
				.orElseThrow(() -> new UnauthorizedException(INVALID));
		if (user.isBlocked()) {
			throw new ForbiddenException("Account is blocked");
		}
		current.setRevokedAt(now);
		repository.save(current);
		return new IssuedRefreshToken(user, create(user.getId(), current.getFamilyId()));
	}

	/** Logout: revokes the whole family of the given token. Unknown tokens are ignored. */
	@Transactional
	public void revoke(String rawToken) {
		repository.findByTokenHash(hash(rawToken))
				.ifPresent(t -> repository.revokeFamily(t.getFamilyId(), clock.instant()));
	}

	@Transactional
	public void revokeAllForUser(Long userId) {
		repository.revokeAllForUser(userId, clock.instant());
	}

	private String create(Long userId, String familyId) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant now = clock.instant();
		RefreshToken entity = new RefreshToken();
		entity.setUserId(userId);
		entity.setTokenHash(hash(raw));
		entity.setFamilyId(familyId);
		entity.setCreatedAt(now);
		entity.setExpiresAt(now.plus(lifetime));
		repository.save(entity);
		return raw;
	}

	private static String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
