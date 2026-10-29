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
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
class LoginAndProfileTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void cleanUp() throws Exception {
		userRepository.deleteAll();
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"Test User","phone":"+380671234567","email":"a@b.com","password":"password123"}
				""")).andExpect(status().isCreated());
	}

	private String login(String email, String password) throws Exception {
		String response = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	@Test
	void logsInWithCaseInsensitiveEmail() throws Exception {
		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"A@B.com\",\"password\":\"password123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"));
	}

	@Test
	void rejectsWrongPassword() throws Exception {
		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"a@b.com\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsUnknownEmail() throws Exception {
		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"nobody@b.com\",\"password\":\"password123\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void locksLoginAfterRepeatedFailuresEvenWithCorrectPassword() throws Exception {
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"Lock","phone":"+380991112299","email":"lock@b.com","password":"password123"}
				""")).andExpect(status().isCreated());

		for (int i = 0; i < 5; i++) {
			mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
							.content("{\"email\":\"lock@b.com\",\"password\":\"wrong-password\"}"))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"lock@b.com\",\"password\":\"password123\"}"))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void locksLoginForUnknownEmailToo() throws Exception {
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
							.content("{\"email\":\"ghost@b.com\",\"password\":\"whatever123\"}"))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"ghost@b.com\",\"password\":\"whatever123\"}"))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void profileRequiresToken() throws Exception {
		mockMvc.perform(get("/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.detail").value("Authentication required"));
	}

	@Test
	void returnsOwnProfile() throws Exception {
		mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + login("a@b.com", "password123")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("a@b.com"))
				.andExpect(jsonPath("$.role").value("USER"));
	}

	private ResultActions patchMe(String json) throws Exception {
		return mockMvc.perform(patch("/users/me")
				.header("Authorization", "Bearer " + login("a@b.com", "password123"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	@Test
	void updatesProfile() throws Exception {
		patchMe("{\"name\":\"New Name\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("New Name"));
	}

	@Test
	void updatesOnlyProvidedFields() throws Exception {
		patchMe("{\"name\":\"Only Name\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Only Name"))
				.andExpect(jsonPath("$.phone").value("+380671234567"));
	}

	@Test
	void rejectsBlankName() throws Exception {
		patchMe("{\"name\":\"   \"}").andExpect(status().isBadRequest());
	}

	@Test
	void patchCannotChangePhone() throws Exception {
		patchMe("{\"name\":\"Same\",\"phone\":\"+380991112233\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phone").value("+380671234567"));
	}

	@Test
	void cannotChangeOwnRole() throws Exception {
		patchMe("{\"name\":\"Hacker\",\"role\":\"ADMIN\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("USER"));
		mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + login("a@b.com", "password123")))
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void registrationIgnoresRequestedRole() throws Exception {
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"Sneaky","phone":"+380991112233","email":"s@b.com","password":"password123","role":"ADMIN"}
				""")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void adminPassesAdminAreaRule() throws Exception {
		User admin = new User();
		admin.setName("Admin");
		admin.setPhone("+380500000000");
		admin.setEmail("admin@b.com");
		admin.setPasswordHash(passwordEncoder.encode("password123"));
		admin.setRole(Role.ADMIN);
		admin.setEmailVerified(true);
		userRepository.saveAndFlush(admin);

		// authorized, but no such handler exists yet, so 404 rather than 401/403
		mockMvc.perform(get("/admin/anything").header("Authorization", "Bearer " + login("admin@b.com", "password123")))
				.andExpect(status().isNotFound());
	}

	@Test
	void userCannotAccessAdminArea() throws Exception {
		mockMvc.perform(get("/admin/anything").header("Authorization", "Bearer " + login("a@b.com", "password123")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.detail").value("Access denied"));
	}
}
