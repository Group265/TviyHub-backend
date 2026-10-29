package team.project.TviyHub.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * Limits requests per client address to the public auth endpoints (login and registration).
 * Uses the connection's remote address; behind a reverse proxy, configure forwarded headers
 * so that this is the real client address.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

	private static final Set<String> LIMITED_PATHS = Set.of(
			"/auth/login", "/auth/register", "/auth/verify-email", "/auth/resend-verification",
			"/auth/forgot-password", "/auth/reset-password", "/auth/refresh", "/auth/logout");

	private final RateLimiter rateLimiter;
	private final long windowSeconds;

	public RateLimitFilter(@Value("${app.rate-limit.max-requests}") int maxRequests,
						   @Value("${app.rate-limit.window-seconds}") long windowSeconds) {
		this.rateLimiter = new RateLimiter(maxRequests, Duration.ofSeconds(windowSeconds));
		this.windowSeconds = windowSeconds;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !HttpMethod.POST.matches(request.getMethod()) || !LIMITED_PATHS.contains(request.getServletPath());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (rateLimiter.tryAcquire(request.getRemoteAddr())) {
			chain.doFilter(request, response);
			return;
		}
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(windowSeconds));
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,"
				+ "\"detail\":\"Too many requests, try again later\"}");
	}
}
