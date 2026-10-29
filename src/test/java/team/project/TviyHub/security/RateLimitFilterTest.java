package team.project.TviyHub.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

	private MockHttpServletResponse send(RateLimitFilter filter, String method, String path) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setServletPath(path);
		request.setRemoteAddr("10.0.0.1");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		return response;
	}

	@Test
	void returns429WithRetryAfterWhenLimitExceeded() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(2, 60);

		assertThat(send(filter, "POST", "/api/auth/login").getStatus()).isEqualTo(200);
		assertThat(send(filter, "POST", "/api/auth/register").getStatus()).isEqualTo(200);

		MockHttpServletResponse blocked = send(filter, "POST", "/api/auth/login");
		assertThat(blocked.getStatus()).isEqualTo(429);
		assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
		assertThat(blocked.getContentAsString()).contains("Too many requests");
	}

	@Test
	void ignoresOtherEndpointsAndMethods() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(1, 60);

		for (int i = 0; i < 5; i++) {
			assertThat(send(filter, "GET", "/api/users/me").getStatus()).isEqualTo(200);
			assertThat(send(filter, "GET", "/api/auth/login").getStatus()).isEqualTo(200);
		}
	}
}
