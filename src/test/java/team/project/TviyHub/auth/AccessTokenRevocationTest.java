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
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfig.class, TestMailConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class AccessTokenRevocationTest {

	private static final String EMAIL = "a@b.com";
	private static final String PASSWORD = "password123";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
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

	private String login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private ResultActions me(String accessToken) throws Exception {
		return mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken));
	}

	private ResultActions postJson(String path, String json) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@Test
	void logoutEndsTheAccessTokenAtOnceButNewLoginWorksInTheSameSecond() throws Exception {
		String response = login(EMAIL, PASSWORD);
		String access = JsonPath.read(response, "$.accessToken");
		String refresh = JsonPath.read(response, "$.refreshToken");
		me(access).andExpect(status().isOk());

		postJson("/api/auth/logout", "{\"refreshToken\":\"%s\"}".formatted(refresh)).andExpect(status().isNoContent());

		me(access).andExpect(status().isUnauthorized());
		String newAccess = JsonPath.read(login(EMAIL, PASSWORD), "$.accessToken");
		me(newAccess).andExpect(status().isOk());
	}

	@Test
	void logoutOnOneDeviceKeepsOtherDeviceUsableThroughRefresh() throws Exception {
		String laptop = login(EMAIL, PASSWORD);
		String phone = login(EMAIL, PASSWORD);
		String phoneAccess = JsonPath.read(phone, "$.accessToken");
		String phoneRefresh = JsonPath.read(phone, "$.refreshToken");

		postJson("/api/auth/logout", "{\"refreshToken\":\"%s\"}"
				.formatted(JsonPath.<String>read(laptop, "$.refreshToken"))).andExpect(status().isNoContent());

		me(phoneAccess).andExpect(status().isUnauthorized());
		String refreshed = postJson("/api/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(phoneRefresh))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		me(JsonPath.read(refreshed, "$.accessToken")).andExpect(status().isOk());
	}

	@Test
	void passwordChangeEndsOldAccessTokens() throws Exception {
		String access = JsonPath.read(login(EMAIL, PASSWORD), "$.accessToken");

		mockMvc.perform(post("/api/users/me/password/request").header("Authorization", "Bearer " + access)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword456\"}"))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/users/me/password/confirm").header("Authorization", "Bearer " + access)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"%s\"}".formatted(mail.lastCode(EMAIL))))
				.andExpect(status().isNoContent());

		me(access).andExpect(status().isUnauthorized());
		String newAccess = JsonPath.read(login(EMAIL, "newpassword456"), "$.accessToken");
		me(newAccess).andExpect(status().isOk());
	}

	@Test
	void passwordResetEndsOldAccessTokens() throws Exception {
		String access = JsonPath.read(login(EMAIL, PASSWORD), "$.accessToken");

		postJson("/api/auth/forgot-password", "{\"email\":\"%s\"}".formatted(EMAIL)).andExpect(status().isNoContent());
		postJson("/api/auth/reset-password", "{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"brandnew789\"}"
				.formatted(EMAIL, mail.lastCode(EMAIL))).andExpect(status().isNoContent());

		me(access).andExpect(status().isUnauthorized());
		me(JsonPath.<String>read(login(EMAIL, "brandnew789"), "$.accessToken")).andExpect(status().isOk());
	}

	@Test
	void adminPasswordResetEndsOldAccessTokens() throws Exception {
		String access = JsonPath.read(login(EMAIL, PASSWORD), "$.accessToken");
		saveUser("Admin", "+380500000000", "admin@b.com", Role.ADMIN);
		String adminAccess = JsonPath.read(login("admin@b.com", PASSWORD), "$.accessToken");

		mockMvc.perform(post("/api/admin/users/" + userId + "/reset-password")
						.header("Authorization", "Bearer " + adminAccess)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"newPassword\":\"adminset123\"}"))
				.andExpect(status().is2xxSuccessful());

		me(access).andExpect(status().isUnauthorized());
		me(adminAccess).andExpect(status().isOk());
	}

	@Test
	void blockAndUnblockKeepOldAccessTokenInvalid() throws Exception {
		String access = JsonPath.read(login(EMAIL, PASSWORD), "$.accessToken");
		saveUser("Admin", "+380500000000", "admin@b.com", Role.ADMIN);
		String adminAccess = JsonPath.read(login("admin@b.com", PASSWORD), "$.accessToken");

		mockMvc.perform(post("/api/admin/users/" + userId + "/block").header("Authorization", "Bearer " + adminAccess))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/admin/users/" + userId + "/unblock").header("Authorization", "Bearer " + adminAccess))
				.andExpect(status().isOk());

		me(access).andExpect(status().isUnauthorized());
	}
}
