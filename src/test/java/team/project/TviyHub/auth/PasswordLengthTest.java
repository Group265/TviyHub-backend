package team.project.TviyHub.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import team.project.TviyHub.TestMailConfig;
import team.project.TviyHub.TestMailConfig.CapturingEmailService;
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.user.UserRepository;
import team.project.TviyHub.verification.PendingRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** bcrypt only looks at the first 72 BYTES, so the limit must count bytes, not characters. */
@Import({TestcontainersConfig.class, TestMailConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class PasswordLengthTest {

	private static final String EMAIL = "a@b.com";
	private static final String ASCII_72 = "a".repeat(72);
	private static final String ASCII_73 = "a".repeat(73);
	/** 36 two-byte letters = 72 bytes: allowed. */
	private static final String CYRILLIC_36 = "ж".repeat(36);
	/** 37 two-byte letters = 74 bytes: too long although only 37 characters. */
	private static final String CYRILLIC_37 = "ж".repeat(37);

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PendingRegistrationRepository pendingRepository;
	@Autowired
	private CapturingEmailService mail;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		pendingRepository.deleteAll();
		mail.reset();
	}

	private ResultActions post(String path, String json, String token) throws Exception {
		var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
				.contentType(MediaType.APPLICATION_JSON).content(json);
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return mockMvc.perform(request);
	}

	private ResultActions register(String password) throws Exception {
		return post("/api/auth/register", """
				{"name":"Olena","phone":"+380671234567","email":"%s","password":"%s","confirmPassword":"%s"}
				""".formatted(EMAIL, password, password), null);
	}

	@Test
	void registrationAcceptsPasswordsUpTo72Bytes() throws Exception {
		register(ASCII_72).andExpect(status().isAccepted());
		pendingRepository.deleteAll();
		register(CYRILLIC_36).andExpect(status().isAccepted());
	}

	@Test
	void registrationRejectsPasswordsOver72Bytes() throws Exception {
		register(ASCII_73).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.password").isNotEmpty());
		register(CYRILLIC_37).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.password").isNotEmpty());
		assertThat(pendingRepository.count()).isZero();
	}

	@Test
	void passwordResetRejectsPasswordsOver72Bytes() throws Exception {
		post("/api/auth/reset-password", "{\"email\":\"%s\",\"code\":\"123456\",\"newPassword\":\"%s\",\"confirmNewPassword\":\"%s\"}"
				.formatted(EMAIL, CYRILLIC_37, CYRILLIC_37), null)
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.newPassword").isNotEmpty());
	}

	@Test
	void passwordChangeRejectsPasswordsOver72Bytes() throws Exception {
		register("password123").andExpect(status().isAccepted());
		String response = post("/api/auth/verify-email", "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(EMAIL, mail.lastCode(EMAIL)), null)
				.andReturn().getResponse().getContentAsString();
		String token = com.jayway.jsonpath.JsonPath.read(response, "$.accessToken");
		post("/api/users/me/password/request", "{\"currentPassword\":\"password123\",\"newPassword\":\"%s\",\"confirmNewPassword\":\"%s\"}"
				.formatted(CYRILLIC_37, CYRILLIC_37), token)
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.newPassword").isNotEmpty());
	}

	@Test
	void loginWithAnOverlongPasswordIsAnOrdinaryFailureNotAServerError() throws Exception {
		post("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, CYRILLIC_37.repeat(5)), null)
				.andExpect(status().isUnauthorized());
	}
}
