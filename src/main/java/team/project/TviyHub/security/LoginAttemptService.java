package team.project.TviyHub.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import team.project.TviyHub.exception.TooManyRequestsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporarily blocks a login key (client address + email) after too many consecutive failures.
 * State is per application instance and is lost on restart.
 */
@Service
public class LoginAttemptService {

	private record State(int failures, Instant lastFailure, Instant lockedUntil) {
	}

	private final int maxFailures;
	private final Duration lockDuration;
	private final Clock clock;
	private final Map<String, State> states = new ConcurrentHashMap<>();

	@Autowired
	public LoginAttemptService(@Value("${app.security.max-login-failures}") int maxFailures,
							   @Value("${app.security.login-lock-minutes}") long lockMinutes) {
		this(maxFailures, Duration.ofMinutes(lockMinutes), Clock.systemUTC());
	}

	LoginAttemptService(int maxFailures, Duration lockDuration, Clock clock) {
		this.maxFailures = maxFailures;
		this.lockDuration = lockDuration;
		this.clock = clock;
	}

	public void assertNotLocked(String key) {
		State state = states.get(key);
		if (state != null && state.lockedUntil() != null) {
			if (clock.instant().isBefore(state.lockedUntil())) {
				throw new TooManyRequestsException("Too many failed login attempts, try again later");
			}
			states.remove(key, state);
		}
	}

	public void recordFailure(String key) {
		Instant now = clock.instant();
		states.compute(key, (k, state) -> {
			boolean expired = state == null || state.lockedUntil() != null
					|| state.lastFailure().plus(lockDuration).isBefore(now);
			int failures = (expired ? 0 : state.failures()) + 1;
			if (failures >= maxFailures) {
				return new State(0, now, now.plus(lockDuration));
			}
			return new State(failures, now, null);
		});
	}

	public void recordSuccess(String key) {
		states.remove(key);
	}
}
