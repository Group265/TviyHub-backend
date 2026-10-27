package team.project.TviyHub.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

	static class MutableClock extends Clock {
		private Instant now = Instant.parse("2026-01-01T00:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	@Test
	void blocksAfterLimitAndAllowsAgainAfterWindow() {
		MutableClock clock = new MutableClock();
		RateLimiter limiter = new RateLimiter(2, Duration.ofSeconds(60), clock);

		assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
		assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
		assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();

		clock.advance(Duration.ofSeconds(61));
		assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
	}

	@Test
	void limitsEachKeySeparately() {
		RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(60), new MutableClock());

		assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
		assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
		assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();
	}
}
