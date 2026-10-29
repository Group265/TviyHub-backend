package team.project.TviyHub.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import team.project.TviyHub.TestMailConfig;
import team.project.TviyHub.TestMailConfig.CapturingEmailService;
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.auth.refresh.RefreshTokenRepository;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfig.class, TestMailConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenFlowTest {

	private static final String EMAIL = "a@b.com";
	private static final String PASSWORD = "password123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private CapturingEmailService mail;

	private Long userId;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		mail.reset();
		userId = saveUser("Test User", "+380671234567", EMAIL, Role.USER).getId();
	}

	private User saveUser(String name, String phone, String email, Role role) {
		User user = new User();
		user.setName(name);
		user.setPhone(phone);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		user.setRole(role);
		user.setEmailVerified(true);
		return userRepository.saveAndFlush(user);
	}

	private ResultActions postJson(String path, String json) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String body(String refreshToken) {
		return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
	}

	private String loginResponse(String email) throws Exception {
		return postJson("/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private String refreshTokenFromLogin() throws Exception {
		return JsonPath.read(loginResponse(EMAIL), "$.refreshToken");
	}

	private String refresh(String refreshToken) throws Exception {
		String response = postJson("/auth/refresh", body(refreshToken))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.refreshToken");
	}

	@Test
	void loginReturnsAccessAndRefreshTokens() throws Exception {
		postJson("/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.expiresInSeconds").value(15 * 60))
				.andExpect(jsonPath("$.refreshExpiresInSeconds").value(30L * 24 * 3600));
	}

	@Test
	void refreshIssuesNewWorkingAccessTokenAndRotatesRefreshToken() throws Exception {
		String first = refreshTokenFromLogin();

		String response = postJson("/auth/refresh", body(first)).andExpect(status().isOk())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		String access = JsonPath.read(response, "$.accessToken");
		String second = JsonPath.read(response, "$.refreshToken");

		assertThat(second).isNotEqualTo(first);
		mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(EMAIL));
		refresh(second);
	}

	@Test
	void reusingAnOldRefreshTokenRevokesTheWholeSession() throws Exception {
		String first = refreshTokenFromLogin();
		String second = refresh(first);

		postJson("/auth/refresh", body(first)).andExpect(status().isUnauthorized());
		// the newer token of the same session is revoked too
		postJson("/auth/refresh", body(second)).andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsUnknownAndExpiredRefreshTokens() throws Exception {
		postJson("/auth/refresh", body("not-a-real-token")).andExpect(status().isUnauthorized());

		String token = refreshTokenFromLogin();
		refreshTokenRepository.findAll().forEach(t -> {
			t.setExpiresAt(Instant.now().minusSeconds(60));
			refreshTokenRepository.save(t);
		});
		postJson("/auth/refresh", body(token)).andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsBlankRefreshToken() throws Exception {
		postJson("/auth/refresh", "{\"refreshToken\":\"\"}").andExpect(status().isBadRequest());
	}

	@Test
	void logoutRevokesSessionAndIsIdempotent() throws Exception {
		String token = refreshTokenFromLogin();

		postJson("/auth/logout", body(token)).andExpect(status().isNoContent());
		postJson("/auth/refresh", body(token)).andExpect(status().isUnauthorized());
		postJson("/auth/logout", body(token)).andExpect(status().isNoContent());
		postJson("/auth/logout", body("unknown")).andExpect(status().isNoContent());
	}

	@Test
	void logoutDoesNotAffectOtherSessions() throws Exception {
		String laptop = refreshTokenFromLogin();
		String phone = refreshTokenFromLogin();

		postJson("/auth/logout", body(laptop)).andExpect(status().isNoContent());

		refresh(phone);
	}

	@Test
	void passwordResetRevokesAllSessions() throws Exception {
		String token = refreshTokenFromLogin();

		postJson("/auth/forgot-password", "{\"email\":\"%s\"}".formatted(EMAIL)).andExpect(status().isNoContent());
		postJson("/auth/reset-password", "{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"brandnew789\"}"
				.formatted(EMAIL, mail.lastCode(EMAIL))).andExpect(status().isNoContent());

		postJson("/auth/refresh", body(token)).andExpect(status().isUnauthorized());
	}

	@Test
	void passwordChangeRevokesAllSessions() throws Exception {
		String loginResponse = loginResponse(EMAIL);
		String access = JsonPath.read(loginResponse, "$.accessToken");
		String token = JsonPath.read(loginResponse, "$.refreshToken");

		mockMvc.perform(post("/users/me/password/request").header("Authorization", "Bearer " + access)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword456\"}"))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/users/me/password/confirm").header("Authorization", "Bearer " + access)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"%s\"}".formatted(mail.lastCode(EMAIL))))
				.andExpect(status().isNoContent());

		postJson("/auth/refresh", body(token)).andExpect(status().isUnauthorized());
	}

	@Test
	void blockingAUserRevokesSessionsAndRefreshIsForbidden() throws Exception {
		String token = refreshTokenFromLogin();
		saveUser("Admin", "+380500000000", "admin@b.com", Role.ADMIN);
		String adminLogin = loginResponse("admin@b.com");
		String adminAccess = JsonPath.read(adminLogin, "$.accessToken");

		mockMvc.perform(post("/admin/users/" + userId + "/block").header("Authorization", "Bearer " + adminAccess))
				.andExpect(status().isOk());

		// the session was revoked, so the token counts as used and the request is refused
		postJson("/auth/refresh", body(token)).andExpect(status().isUnauthorized());
	}

	@Test
	void blockedUserCannotRefreshAnUnrevokedToken() throws Exception {
		String token = refreshTokenFromLogin();
		User user = userRepository.findById(userId).orElseThrow();
		user.setBlocked(true);
		userRepository.saveAndFlush(user);

		postJson("/auth/refresh", body(token)).andExpect(status().isForbidden());
	}
}
