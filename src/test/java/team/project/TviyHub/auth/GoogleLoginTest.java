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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import team.project.TviyHub.TestMailConfig;
import team.project.TviyHub.TestMailConfig.CapturingEmailService;
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.auth.google.GoogleIdTokenVerifier;
import team.project.TviyHub.auth.google.GoogleProfile;
import team.project.TviyHub.exception.UnauthorizedException;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfig.class, TestMailConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class GoogleLoginTest {

	private static final String EMAIL = "g@b.com";
	private static final String PASSWORD = "password123";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private CapturingEmailService mail;
	@MockitoBean
	private GoogleIdTokenVerifier verifier;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		mail.reset();
	}

	private void googleReturns(String sub, String email, boolean verified, String name) {
		when(verifier.verify(anyString())).thenReturn(new GoogleProfile(sub, email, verified, name));
	}

	private ResultActions google() throws Exception {
		return mockMvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
				.content("{\"idToken\":\"any-token\"}"));
	}

	private ResultActions me(String access) throws Exception {
		return mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access));
	}

	private ResultActions passwordLogin(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	private User saveUser(String email, String phone, boolean verified) {
		User user = new User();
		user.setName("Existing");
		user.setPhone(phone);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		user.setRole(Role.USER);
		user.setEmailVerified(verified);
		return userRepository.saveAndFlush(user);
	}

	@Test
	void firstSignInCreatesAccountWithoutPhoneAndPassword() throws Exception {
		googleReturns("sub-1", "G@B.com", true, "Gala Google");

		String response = google().andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		String access = JsonPath.read(response, "$.accessToken");
		me(access).andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(EMAIL))
				.andExpect(jsonPath("$.name").value("Gala Google"))
				.andExpect(jsonPath("$.phone").doesNotExist())
				.andExpect(jsonPath("$.emailVerified").value(true))
				.andExpect(jsonPath("$.role").value("USER"));

		// second sign-in is the same user
		google().andExpect(status().isOk());
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void usesEmailPrefixWhenGoogleSendsNoName() throws Exception {
		googleReturns("sub-1", EMAIL, true, null);
		String access = JsonPath.read(google().andReturn().getResponse().getContentAsString(), "$.accessToken");
		me(access).andExpect(jsonPath("$.name").value("g"));
	}

	@Test
	void linksExistingVerifiedAccountAndKeepsThePassword() throws Exception {
		saveUser(EMAIL, "+380671234567", true);
		googleReturns("sub-1", EMAIL, true, "Gala");

		google().andExpect(status().isOk());

		assertThat(userRepository.count()).isEqualTo(1);
		assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getGoogleId()).isEqualTo("sub-1");
		passwordLogin(EMAIL, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void linkingAnUnverifiedAccountDropsThePasswordAndOldSessions() throws Exception {
		saveUser(EMAIL, "+380671234567", false);
		googleReturns("sub-1", EMAIL, true, "Gala");

		google().andExpect(status().isOk());

		User user = userRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(user.isEmailVerified()).isTrue();
		assertThat(user.getPasswordHash()).isNull();
		passwordLogin(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsUnverifiedGoogleEmailInvalidTokenAndBlankToken() throws Exception {
		googleReturns("sub-1", EMAIL, false, "Gala");
		google().andExpect(status().isUnauthorized());

		when(verifier.verify(anyString())).thenThrow(new UnauthorizedException("Invalid Google token"));
		google().andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"\"}"))
				.andExpect(status().isBadRequest());
		assertThat(userRepository.count()).isZero();
	}

	@Test
	void blockedAccountCannotSignIn() throws Exception {
		User user = saveUser(EMAIL, "+380671234567", true);
		user.setBlocked(true);
		userRepository.saveAndFlush(user);
		googleReturns("sub-1", EMAIL, true, "Gala");

		google().andExpect(status().isForbidden());
	}

	@Test
	void googleAccountCannotUsePasswordLoginUntilItSetsOneByReset() throws Exception {
		googleReturns("sub-1", EMAIL, true, "Gala");
		String access = JsonPath.read(google().andReturn().getResponse().getContentAsString(), "$.accessToken");

		passwordLogin(EMAIL, "").andExpect(status().isBadRequest());
		passwordLogin(EMAIL, PASSWORD).andExpect(status().isUnauthorized());

		// actions that need the current password are refused with a hint
		mockMvc.perform(post("/api/users/me/password/request").header("Authorization", "Bearer " + access)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"currentPassword\":\"whatever123\",\"newPassword\":\"newpassword456\"}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\"}".formatted(EMAIL))).andExpect(status().isNoContent());
		mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"brandnew789\"}"
								.formatted(EMAIL, mail.lastCode(EMAIL))))
				.andExpect(status().isNoContent());
		passwordLogin(EMAIL, "brandnew789").andExpect(status().isOk());
	}

	@Test
	void googleAccountCanAddAPhoneLater() throws Exception {
		googleReturns("sub-1", EMAIL, true, "Gala");
		String access = JsonPath.read(google().andReturn().getResponse().getContentAsString(), "$.accessToken");

		mockMvc.perform(post("/api/users/me/phone/request").header("Authorization", "Bearer " + access)
						.contentType(MediaType.APPLICATION_JSON).content("{\"newPhone\":\"+380991112233\"}"))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/users/me/phone/confirm").header("Authorization", "Bearer " + access)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"%s\"}".formatted(mail.lastCode(EMAIL))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phone").value("+380991112233"));
	}
}
