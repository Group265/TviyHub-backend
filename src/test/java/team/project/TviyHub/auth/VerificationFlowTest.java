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
import team.project.TviyHub.verification.PendingRegistrationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
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

	@Autowired
	private PendingRegistrationRepository pendingRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() throws Exception {
		userRepository.deleteAll();
		pendingRepository.deleteAll();
		mail.reset();
		register("Test User", "+380671234567", EMAIL);
	}

	private void register(String name, String phone, String email) throws Exception {
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"%s","phone":"%s","email":"%s","password":"%s","confirmPassword":"%s"}
								""".formatted(name, phone, email, PASSWORD, PASSWORD)))
				.andExpect(status().isAccepted());
	}

	private ResultActions postJson(String path, String json) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions postAs(String token, String path, String json) throws Exception {
		return mockMvc.perform(post(path).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String verify(String email) throws Exception {
		String response = postJson("/api/auth/verify-email", "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, mail.lastCode(email)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	private String login(String email, String password) throws Exception {
		String response = postJson("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
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
	void registrationSendsCodeAndVerificationCreatesVerifiedSignedInUser() throws Exception {
		assertThat(mail.lastCode(EMAIL)).hasSize(6);
		assertThat(userRepository.count()).isZero();

		String token = verify(EMAIL);

		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.emailVerified").value(true));
	}

	@Test
	void codeCannotBeReused() throws Exception {
		String code = mail.lastCode(EMAIL);
		verify(EMAIL);
		postJson("/api/auth/verify-email", "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(EMAIL, code))
				.andExpect(status().isBadRequest());
	}

	@Test
	void wrongCodeIsRejectedAndInvalidatedAfterMaxAttempts() throws Exception {
		String good = mail.lastCode(EMAIL);
		String bad = wrongCode(EMAIL);
		for (int i = 0; i < 5; i++) {
			postJson("/api/auth/verify-email", "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(EMAIL, bad))
					.andExpect(status().isBadRequest());
		}
		postJson("/api/auth/verify-email", "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(EMAIL, good))
				.andExpect(status().isBadRequest());

		// the pending registration is gone: registering again gives a fresh code
		register("Test User", "+380671234567", EMAIL);
		verify(EMAIL);
	}

	@Test
	void forgotDoesNotRevealWhetherEmailExists() throws Exception {
		postJson("/api/auth/forgot-password", "{\"email\":\"nobody@b.com\"}").andExpect(status().isNoContent());
		assertThat(mail.sentCount("nobody@b.com")).isZero();
	}

	@Test
	void unverifiedUserCannotStartSensitiveChanges() throws Exception {
		// an account registered before the code became mandatory
		User legacy = new User();
		legacy.setName("Legacy");
		legacy.setPhone("+380500000001");
		legacy.setEmail("legacy@b.com");
		legacy.setPasswordHash(passwordEncoder.encode(PASSWORD));
		legacy.setRole(Role.USER);
		legacy.setEmailVerified(false);
		userRepository.saveAndFlush(legacy);
		String token = login("legacy@b.com", PASSWORD);
		postAs(token, "/api/users/me/password/request",
				"{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword456\",\"confirmNewPassword\":\"newpassword456\"}").andExpect(status().isForbidden());
		postAs(token, "/api/users/me/phone/request", "{\"newPhone\":\"+380991112233\"}").andExpect(status().isForbidden());
		postAs(token, "/api/users/me/email/request",
				"{\"newEmail\":\"n@b.com\",\"currentPassword\":\"password123\"}").andExpect(status().isForbidden());
	}

	@Test
	void changesPasswordWithCode() throws Exception {
		String token = verifiedToken();
		postAs(token, "/api/users/me/password/request",
				"{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword456\",\"confirmNewPassword\":\"newpassword456\"}").andExpect(status().isNoContent());

		// not applied before confirmation
		login(EMAIL, PASSWORD);

		postAs(token, "/api/users/me/password/confirm", "{\"code\":\"%s\"}".formatted(wrongCode(EMAIL)))
				.andExpect(status().isBadRequest());
		postAs(token, "/api/users/me/password/confirm", "{\"code\":\"%s\"}".formatted(mail.lastCode(EMAIL)))
				.andExpect(status().isNoContent());

		login(EMAIL, "newpassword456");
		postJson("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void passwordChangeRequestChecksCurrentPasswordAndLength() throws Exception {
		String token = verifiedToken();
		postAs(token, "/api/users/me/password/request",
				"{\"currentPassword\":\"wrong-password\",\"newPassword\":\"newpassword456\",\"confirmNewPassword\":\"newpassword456\"}").andExpect(status().isBadRequest());
		postAs(token, "/api/users/me/password/request",
				"{\"currentPassword\":\"password123\",\"newPassword\":\"short\",\"confirmNewPassword\":\"short\"}").andExpect(status().isBadRequest());
	}

	@Test
	void passwordChangeRejectsNewPasswordEqualToCurrent() throws Exception {
		String token = verifiedToken();
		int sentBefore = mail.sentCount(EMAIL);
		postAs(token, "/api/users/me/password/request",
				"{\"currentPassword\":\"password123\",\"newPassword\":\"password123\",\"confirmNewPassword\":\"password123\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("New password must be different from the current password"));
		org.assertj.core.api.Assertions.assertThat(mail.sentCount(EMAIL)).isEqualTo(sentBefore);
		// the session and the old password keep working
		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
		login(EMAIL, PASSWORD);
		// a different password is still accepted
		postAs(token, "/api/users/me/password/request",
				"{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword456\",\"confirmNewPassword\":\"newpassword456\"}")
				.andExpect(status().isNoContent());
	}

	@Test
	void changesPhoneWithCode() throws Exception {
		String token = verifiedToken();
		postAs(token, "/api/users/me/phone/request", "{\"newPhone\":\"+380991112233\"}").andExpect(status().isNoContent());
		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.phone").value("+380671234567"));

		postAs(token, "/api/users/me/phone/confirm", "{\"code\":\"%s\"}".formatted(mail.lastCode(EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phone").value("+380991112233"));
	}

	@Test
	void rejectsPhoneUsedByAnotherUser() throws Exception {
		register("Other", "+380991112233", "c@d.com");
		verify("c@d.com");
		String token = verifiedToken();
		postAs(token, "/api/users/me/phone/request", "{\"newPhone\":\"+380991112233\"}").andExpect(status().isConflict());
	}

	@Test
	void changesEmailWithCodeSentToNewAddress() throws Exception {
		String token = verifiedToken();
		postAs(token, "/api/users/me/email/request",
				"{\"newEmail\":\"New@B.com\",\"currentPassword\":\"password123\"}").andExpect(status().isNoContent());
		assertThat(mail.lastCode("new@b.com")).hasSize(6);

		postAs(token, "/api/users/me/email/confirm", "{\"code\":\"%s\"}".formatted(mail.lastCode("new@b.com")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("new@b.com"));

		login("new@b.com", PASSWORD);
		postJson("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsEmailChangeWithWrongPasswordOrTakenEmail() throws Exception {
		register("Other", "+380991112233", "c@d.com");
		verify("c@d.com");
		String token = verifiedToken();
		postAs(token, "/api/users/me/email/request",
				"{\"newEmail\":\"x@b.com\",\"currentPassword\":\"wrong-password\"}").andExpect(status().isBadRequest());
		postAs(token, "/api/users/me/email/request",
				"{\"newEmail\":\"not-an-email\",\"currentPassword\":\"password123\"}").andExpect(status().isBadRequest());
		postAs(token, "/api/users/me/email/request",
				"{\"newEmail\":\"C@d.com\",\"currentPassword\":\"password123\"}").andExpect(status().isConflict());
	}

	@Test
	void resetsForgottenPasswordWithCode() throws Exception {
		verify(EMAIL);
		postJson("/api/auth/forgot-password", "{\"email\":\"%s\"}".formatted(EMAIL)).andExpect(status().isNoContent());
		String code = mail.lastCode(EMAIL);

		postJson("/api/auth/reset-password", "{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"brandnew789\",\"confirmNewPassword\":\"brandnew789\"}"
				.formatted(EMAIL, wrongCode(EMAIL))).andExpect(status().isBadRequest());
		postJson("/api/auth/reset-password", "{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"brandnew789\",\"confirmNewPassword\":\"brandnew789\"}"
				.formatted(EMAIL, code)).andExpect(status().isNoContent());

		login(EMAIL, "brandnew789");
	}
}
