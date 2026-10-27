package team.project.TviyHub.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding window limiter: at most {@code maxRequests} per key within {@code window}.
 * State is per application instance and is lost on restart.
 */
public class RateLimiter {

	private static final int EVICTION_THRESHOLD = 10_000;

	private final int maxRequests;
	private final Duration window;
	private final Clock clock;
	private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

	public RateLimiter(int maxRequests, Duration window) {
		this(maxRequests, window, Clock.systemUTC());
	}

	RateLimiter(int maxRequests, Duration window, Clock clock) {
		this.maxRequests = maxRequests;
		this.window = window;
		this.clock = clock;
	}

	public boolean tryAcquire(String key) {
		Instant now = clock.instant();
		if (hits.size() > EVICTION_THRESHOLD) {
			evictStale(now);
		}
		Deque<Instant> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (queue) {
			Instant cutoff = now.minus(window);
			while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
				queue.pollFirst();
			}
			if (queue.size() >= maxRequests) {
				return false;
			}
			queue.addLast(now);
			return true;
		}
	}

	private void evictStale(Instant now) {
		Instant cutoff = now.minus(window);
		hits.entrySet().removeIf(e -> {
			synchronized (e.getValue()) {
				return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(cutoff);
			}
		});
	}
}
