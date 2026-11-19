package team.project.TviyHub.auth;

import com.jayway.jsonpath.JsonPath;
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

@Import({TestcontainersConfig.class, TestMailConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class PasswordConfirmationTest {

	private static final String EMAIL = "a@b.com";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PendingRegistrationRepository pendingRepository;
	@Autowired
	private CapturingEmailService mail;

	@BeforeEach
	void setUp() throws Exception {
		userRepository.deleteAll();
		pendingRepository.deleteAll();
		mail.reset();
	}

	private ResultActions postJson(String path, String json, String token) throws Exception {
		var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json);
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return mockMvc.perform(request);
	}

	private String registerAndSignIn() throws Exception {
		postJson("/api/auth/register", """
				{"name":"T","phone":"+380671234567","email":"%s","password":"password123","confirmPassword":"password123"}
				""".formatted(EMAIL), null).andExpect(status().isAccepted());
		String response = postJson("/api/auth/verify-email",
				"{\"email\":\"%s\",\"code\":\"%s\"}".formatted(EMAIL, mail.lastCode(EMAIL)), null)
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	@Test
	void registrationRejectsDifferentRepeatedPassword() throws Exception {
		postJson("/api/auth/register", """
				{"name":"T","phone":"+380671234567","email":"%s","password":"password123","confirmPassword":"password124"}
				""".formatted(EMAIL), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.confirmPassword").value("passwords do not match"));
		assertThat(pendingRepository.count()).isZero();
		assertThat(mail.sentCount(EMAIL)).isZero();
	}

	@Test
	void registrationRequiresTheRepeatedPassword() throws Exception {
		postJson("/api/auth/register", """
				{"name":"T","phone":"+380671234567","email":"%s","password":"password123"}
				""".formatted(EMAIL), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.confirmPassword").exists());
	}

	@Test
	void passwordResetRejectsDifferentRepeatedPassword() throws Exception {
		registerAndSignIn();
		postJson("/api/auth/forgot-password", "{\"email\":\"%s\"}".formatted(EMAIL), null).andExpect(status().isNoContent());
		postJson("/api/auth/reset-password", "{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"brandnew789\",\"confirmNewPassword\":\"brandnew000\"}"
				.formatted(EMAIL, mail.lastCode(EMAIL)), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.confirmNewPassword").value("passwords do not match"));
		postJson("/api/auth/login", "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(EMAIL), null)
				.andExpect(status().isOk());
	}

	@Test
	void passwordResetRequiresTheRepeatedPassword() throws Exception {
		postJson("/api/auth/reset-password", "{\"email\":\"%s\",\"code\":\"123456\",\"newPassword\":\"brandnew789\"}"
				.formatted(EMAIL), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.confirmNewPassword").exists());
	}

	@Test
	void passwordChangeRejectsDifferentRepeatedPassword() throws Exception {
		String token = registerAndSignIn();
		postJson("/api/users/me/password/request",
				"{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword456\",\"confirmNewPassword\":\"newpassword457\"}", token)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.confirmNewPassword").value("passwords do not match"));
		postJson("/api/users/me/password/request",
				"{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword456\"}", token)
				.andExpect(status().isBadRequest());
	}
}
