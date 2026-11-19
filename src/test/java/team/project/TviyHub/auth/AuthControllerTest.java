package team.project.TviyHub.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import team.project.TviyHub.TestMailConfig;
import team.project.TviyHub.TestMailConfig.CapturingEmailService;
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.verification.PendingRegistrationRepository;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfig.class, TestMailConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private CapturingEmailService mail;

	@Autowired
	private PendingRegistrationRepository pendingRepository;

	@BeforeEach
	void cleanUp() {
		userRepository.deleteAll();
		pendingRepository.deleteAll();
		mail.reset();
	}

	private static String body(String phone, String email, String password) {
		return """
				{"name":"Test User","phone":"%s","email":"%s","password":"%s","confirmPassword":"%s"}
				""".formatted(phone, email, password, password);
	}

	private void register(String json, int expectedStatus) throws Exception {
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().is(expectedStatus));
	}

	private void confirm(String email) throws Exception {
		mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, mail.lastCode(email))))
				.andExpect(status().isOk());
	}

	@Test
	void registrationCreatesNoAccountUntilCodeIsConfirmed() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("+380671234567", "A@B.com", "password123")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.email").value("a@b.com"))
				.andExpect(jsonPath("$.codeExpiresInSeconds").value(600))
				.andExpect(jsonPath("$.id").doesNotExist());

		assertThat(userRepository.count()).isZero();
		assertThat(mail.lastCode("a@b.com")).hasSize(6);
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"a@b.com\",\"password\":\"password123\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void confirmingTheCodeCreatesVerifiedUserAndSignsIn() throws Exception {
		register(body("+380671234567", "A@B.com", "password123"), 202);

		String response = mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"a@b.com\",\"code\":\"%s\"}".formatted(mail.lastCode("a@b.com"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		User saved = userRepository.findAll().get(0);
		assertThat(saved.getRole()).isEqualTo(Role.USER);
		assertThat(saved.isEmailVerified()).isTrue();
		assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
		assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
		assertThat(pendingRepository.count()).isZero();

		String token = com.jayway.jsonpath.JsonPath.read(response, "$.accessToken");
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/users/me")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("a@b.com"))
				.andExpect(jsonPath("$.emailVerified").value(true));
	}

	@Test
	void registeringAgainReplacesThePendingRegistrationOfThatEmail() throws Exception {
		register(body("+380671234567", "a@b.com", "password123"), 202);
		register(body("+380671234567", "a@b.com", "password123"), 202);
		assertThat(pendingRepository.count()).isEqualTo(1);
		assertThat(mail.sentCount("a@b.com")).isEqualTo(2);
		confirm("a@b.com");
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void rejectsConfirmationWhenPhoneWasTakenMeanwhile() throws Exception {
		register(body("+380671234567", "a@b.com", "password123"), 202);
		register(body("+380671234567", "c@d.com", "password123"), 202);
		confirm("a@b.com");
		mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"c@d.com\",\"code\":\"%s\"}".formatted(mail.lastCode("c@d.com"))))
				.andExpect(status().isConflict());
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void wrongCodeNeverCreatesAnAccount() throws Exception {
		register(body("+380671234567", "a@b.com", "password123"), 202);
		String bad = "000000".equals(mail.lastCode("a@b.com")) ? "111111" : "000000";
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
							.content("{\"email\":\"a@b.com\",\"code\":\"%s\"}".formatted(bad)))
					.andExpect(status().isBadRequest());
		}
		mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"a@b.com\",\"code\":\"%s\"}".formatted(mail.lastCode("a@b.com"))))
				.andExpect(status().isBadRequest());
		assertThat(userRepository.count()).isZero();
		assertThat(pendingRepository.count()).isZero();
	}

	@Test
	void rejectsDuplicateEmail() throws Exception {
		register(body("+380671234567", "a@b.com", "password123"), 202);
		confirm("a@b.com");
		register(body("+380671234568", "A@b.com", "password123"), 409);
	}

	@Test
	void rejectsDuplicatePhone() throws Exception {
		register(body("+380671234567", "a@b.com", "password123"), 202);
		confirm("a@b.com");
		register(body("+380671234567", "c@d.com", "password123"), 409);
	}

	@Test
	void rejectsInvalidPhone() throws Exception {
		register(body("0671234567", "a@b.com", "password123"), 400);
	}

	@Test
	void acceptsInternationalPhone() throws Exception {
		register(body("+14155552671", "us@b.com", "password123"), 202);
		register(body("+447911123456", "uk@b.com", "password123"), 202);
	}

	@Test
	void rejectsPhoneWithLettersOrTooManyDigits() throws Exception {
		register(body("+38067abc4567", "a@b.com", "password123"), 400);
		register(body("+1234567890123456", "c@d.com", "password123"), 400);
	}

	@Test
	void rejectsInvalidEmail() throws Exception {
		register(body("+380671234567", "not-an-email", "password123"), 400);
	}

	@Test
	void rejectsShortPassword() throws Exception {
		register(body("+380671234567", "a@b.com", "short"), 400);
	}
}
