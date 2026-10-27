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
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void cleanUp() {
		userRepository.deleteAll();
	}

	private static String body(String phone, String email, String password) {
		return """
				{"name":"Test User","phone":"%s","email":"%s","password":"%s"}
				""".formatted(phone, email, password);
	}

	private void register(String json, int expectedStatus) throws Exception {
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().is(expectedStatus));
	}

	@Test
	void registersUser() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("+380671234567", "A@B.com", "password123")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.email").value("a@b.com"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		User saved = userRepository.findAll().get(0);
		assertThat(saved.getRole()).isEqualTo(Role.USER);
		assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
		assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
	}

	@Test
	void rejectsDuplicateEmail() throws Exception {
		register(body("+380671234567", "a@b.com", "password123"), 201);
		register(body("+380671234568", "A@b.com", "password123"), 409);
	}

	@Test
	void rejectsDuplicatePhone() throws Exception {
		register(body("+380671234567", "a@b.com", "password123"), 201);
		register(body("+380671234567", "c@d.com", "password123"), 409);
	}

	@Test
	void rejectsInvalidPhone() throws Exception {
		register(body("0671234567", "a@b.com", "password123"), 400);
	}

	@Test
	void acceptsInternationalPhone() throws Exception {
		register(body("+14155552671", "us@b.com", "password123"), 201);
		register(body("+447911123456", "uk@b.com", "password123"), 201);
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
