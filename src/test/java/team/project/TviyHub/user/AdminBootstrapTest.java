package team.project.TviyHub.user;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import team.project.TviyHub.TestcontainersConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminBootstrapTest {

	private static final String EMAIL = "admin@admin.com";
	private static final String PASSWORD = "12345678";

	@Autowired
	private AdminBootstrap bootstrap;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		userRepository.deleteAll();
	}

	@Test
	void createsTheDefaultAdministratorWhoCanLogInAndOpenTheAdminPanel() throws Exception {
		bootstrap.ensureAdmin();

		User admin = userRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(admin.isEmailVerified()).isTrue();
		assertThat(admin.getPhone()).isNull();

		String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(response, "$.accessToken");
		mockMvc.perform(get("/api/admin/users/stats").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
	}

	@Test
	void runningTwiceDoesNotCreateAnotherAdministrator() {
		bootstrap.ensureAdmin();
		bootstrap.ensureAdmin();

		assertThat(userRepository.countByRole(Role.ADMIN)).isEqualTo(1);
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void doesNothingWhenAnAdministratorAlreadyExists() {
		User other = new User();
		other.setName("Boss");
		other.setEmail("boss@b.com");
		other.setPasswordHash(passwordEncoder.encode("whatever123"));
		other.setRole(Role.ADMIN);
		other.setEmailVerified(true);
		userRepository.saveAndFlush(other);

		bootstrap.ensureAdmin();

		assertThat(userRepository.findByEmail(EMAIL)).isEmpty();
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void promotesAnExistingUserWithTheConfiguredEmailAndKeepsTheirPassword() {
		User user = new User();
		user.setName("Olena");
		user.setPhone("+380671234567");
		user.setEmail(EMAIL);
		user.setPasswordHash(passwordEncoder.encode("OwnPassword1"));
		user.setRole(Role.USER);
		user.setEmailVerified(false);
		userRepository.saveAndFlush(user);

		bootstrap.ensureAdmin();

		User promoted = userRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(promoted.getRole()).isEqualTo(Role.ADMIN);
		assertThat(promoted.isEmailVerified()).isTrue();
		assertThat(passwordEncoder.matches("OwnPassword1", promoted.getPasswordHash())).isTrue();
		assertThat(userRepository.count()).isEqualTo(1);
	}
}
