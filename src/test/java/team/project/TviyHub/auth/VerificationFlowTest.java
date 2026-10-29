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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfig.class, TestMailConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class VerificationFlowTest {

	private static final String EMAIL = "a@b.com";
	private static final String PASSWORD = "password123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CapturingEmailService mail;

	@BeforeEach
	void setUp() throws Exception {
		userRepository.deleteAll();
		mail.reset();
		register("Test User", "+380671234567", EMAIL);
	}

	private void register(String name, String phone, String email) throws Exception {
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"%s","phone":"%s","email":"%s","password":"%s"}
								""".formatted(name, phone, email, PASSWORD)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.emailVerified").value(false));
	}

	private ResultActions postJson(String path, String json) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions postAs(String token, String path, String json) throws Exception {
		return mockMvc.perform(post(path).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private void verify(String email) throws Exception {
		postJson("/auth/verify-email", "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, mail.lastCode(email)))
				.andExpect(status().isNoContent());
	}

	private String login(String email, String password) throws Exception {
		String response = postJson("/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	private String verifiedToken() throws Exception {
		verify(EMAIL);
		return login(EMAIL, PASSWORD);
	}

	private String wrongCode(String email) {
		return "000000".equals(mail.lastCode(email)) ? "111111" : "000000";
	}

	@Test
	void registrationSendsCodeAndVerificationMarksEmailVerified() throws Exception {
		assertThat(mail.lastCode(EMAIL)).hasSize(6);
		String token = login(EMAIL, PASSWORD);
		mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.emailVerified").value(false));

		verify(EMAIL);

		mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.emailVerified").value(true));
	}

	@Test
	void codeCannotBeReused() throws Exception {
		String code = mail.lastCode(EMAIL);
		verify(EMAIL);
		postJson("/auth/verify-email", "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(EMAIL, code))
				.andExpect(status().isBadRequest());
	}

	@Test
	void wrongCodeIsRejectedAndInvalidatedAfterMaxAttempts() throws Exception {
		String good = mail.lastCode(EMAIL);
		String bad = wrongCode(EMAIL);
		for (int i = 0; i < 5; i++) {
			postJson("/auth/verify-email", "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(EMAIL, bad))
					.andExpect(status().isBadRequest());
		}
		postJson("/auth/verify-email", "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(EMAIL, good))
				.andExpect(status().isBadRequest());

		// a fresh code works again
		postJson("/auth/resend-verification", "{\"email\":\"%s\"}".formatted(EMAIL)).andExpect(status().isNoContent());
		verify(EMAIL);
	}

	@Test
	void resendAndForgotDoNotRevealWhetherEmailExists() throws Exception {
		postJson("/auth/resend-verification", "{\"email\":\"nobody@b.com\"}").andExpect(status().isNoContent());
		postJson("/auth/forgot-password", "{\"email\":\"nobody@b.com\"}").andExpect(status().isNoContent());
		assertThat(mail.sentCount("nobody@b.com")).isZero();
	}

	@Test
	void unverifiedUserCannotStartSensitiveChanges() throws Exception {
		String token = login(EMAIL, PASSWORD);
		postAs(token, "/users/me/password/request",
				"{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword456\"}").andExpect(status().isForbidden());
		postAs(token, "/users/me/phone/request", "{\"newPhone\":\"+380991112233\"}").andExpect(status().isForbidden());
		postAs(token, "/users/me/email/request",
				"{\"newEmail\":\"n@b.com\",\"currentPassword\":\"password123\"}").andExpect(status().isForbidden());
	}

	@Test
	void changesPasswordWithCode() throws Exception {
		String token = verifiedToken();
		postAs(token, "/users/me/password/request",
				"{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword456\"}").andExpect(status().isNoContent());

		// not applied before confirmation
		login(EMAIL, PASSWORD);

		postAs(token, "/users/me/password/confirm", "{\"code\":\"%s\"}".formatted(wrongCode(EMAIL)))
				.andExpect(status().isBadRequest());
		postAs(token, "/users/me/password/confirm", "{\"code\":\"%s\"}".formatted(mail.lastCode(EMAIL)))
				.andExpect(status().isNoContent());

		login(EMAIL, "newpassword456");
		postJson("/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void passwordChangeRequestChecksCurrentPasswordAndLength() throws Exception {
		String token = verifiedToken();
		postAs(token, "/users/me/password/request",
				"{\"currentPassword\":\"wrong-password\",\"newPassword\":\"newpassword456\"}").andExpect(status().isBadRequest());
		postAs(token, "/users/me/password/request",
				"{\"currentPassword\":\"password123\",\"newPassword\":\"short\"}").andExpect(status().isBadRequest());
	}

	@Test
	void changesPhoneWithCode() throws Exception {
		String token = verifiedToken();
		postAs(token, "/users/me/phone/request", "{\"newPhone\":\"+380991112233\"}").andExpect(status().isNoContent());
		mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.phone").value("+380671234567"));

		postAs(token, "/users/me/phone/confirm", "{\"code\":\"%s\"}".formatted(mail.lastCode(EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phone").value("+380991112233"));
	}

	@Test
	void rejectsPhoneUsedByAnotherUser() throws Exception {
		register("Other", "+380991112233", "c@d.com");
		String token = verifiedToken();
		postAs(token, "/users/me/phone/request", "{\"newPhone\":\"+380991112233\"}").andExpect(status().isConflict());
	}

	@Test
	void changesEmailWithCodeSentToNewAddress() throws Exception {
		String token = verifiedToken();
		postAs(token, "/users/me/email/request",
				"{\"newEmail\":\"New@B.com\",\"currentPassword\":\"password123\"}").andExpect(status().isNoContent());
		assertThat(mail.lastCode("new@b.com")).hasSize(6);

		postAs(token, "/users/me/email/confirm", "{\"code\":\"%s\"}".formatted(mail.lastCode("new@b.com")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("new@b.com"));

		login("new@b.com", PASSWORD);
		postJson("/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsEmailChangeWithWrongPasswordOrTakenEmail() throws Exception {
		register("Other", "+380991112233", "c@d.com");
		String token = verifiedToken();
		postAs(token, "/users/me/email/request",
				"{\"newEmail\":\"x@b.com\",\"currentPassword\":\"wrong-password\"}").andExpect(status().isBadRequest());
		postAs(token, "/users/me/email/request",
				"{\"newEmail\":\"not-an-email\",\"currentPassword\":\"password123\"}").andExpect(status().isBadRequest());
		postAs(token, "/users/me/email/request",
				"{\"newEmail\":\"C@d.com\",\"currentPassword\":\"password123\"}").andExpect(status().isConflict());
	}

	@Test
	void resetsForgottenPasswordWithCode() throws Exception {
		postJson("/auth/forgot-password", "{\"email\":\"%s\"}".formatted(EMAIL)).andExpect(status().isNoContent());
		String code = mail.lastCode(EMAIL);

		postJson("/auth/reset-password", "{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"brandnew789\"}"
				.formatted(EMAIL, wrongCode(EMAIL))).andExpect(status().isBadRequest());
		postJson("/auth/reset-password", "{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"brandnew789\"}"
				.formatted(EMAIL, code)).andExpect(status().isNoContent());

		login(EMAIL, "brandnew789");
	}
}
