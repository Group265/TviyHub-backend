package team.project.TviyHub.security;

import org.junit.jupiter.api.Test;
import team.project.TviyHub.exception.TooManyRequestsException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

	private final RateLimiterTest.MutableClock clock = new RateLimiterTest.MutableClock();
	private final LoginAttemptService service = new LoginAttemptService(3, Duration.ofMinutes(15), clock);

	@Test
	void locksAfterMaxConsecutiveFailures() {
		service.recordFailure("k");
		service.recordFailure("k");
		assertThatCode(() -> service.assertNotLocked("k")).doesNotThrowAnyException();

		service.recordFailure("k");
		assertThatThrownBy(() -> service.assertNotLocked("k")).isInstanceOf(TooManyRequestsException.class);
	}

	@Test
	void unlocksAfterLockDuration() {
		for (int i = 0; i < 3; i++) {
			service.recordFailure("k");
		}
		clock.advance(Duration.ofMinutes(16));

		assertThatCode(() -> service.assertNotLocked("k")).doesNotThrowAnyException();
	}

	@Test
	void successResetsFailureCount() {
		service.recordFailure("k");
		service.recordFailure("k");
		service.recordSuccess("k");
		service.recordFailure("k");
		service.recordFailure("k");

		assertThatCode(() -> service.assertNotLocked("k")).doesNotThrowAnyException();
	}

	@Test
	void locksEachKeySeparately() {
		for (int i = 0; i < 3; i++) {
			service.recordFailure("a");
		}

		assertThatCode(() -> service.assertNotLocked("b")).doesNotThrowAnyException();
	}
}
