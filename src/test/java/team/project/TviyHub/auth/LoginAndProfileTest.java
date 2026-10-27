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
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.user.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
	void profileRequiresToken() throws Exception {
		mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void returnsOwnProfile() throws Exception {
		mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + login("a@b.com", "password123")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("a@b.com"))
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void updatesProfile() throws Exception {
		mockMvc.perform(put("/users/me")
						.header("Authorization", "Bearer " + login("a@b.com", "password123"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"New Name\",\"phone\":\"+380991112233\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("New Name"))
				.andExpect(jsonPath("$.phone").value("+380991112233"));
	}

	@Test
	void userCannotAccessAdminArea() throws Exception {
		mockMvc.perform(get("/admin/anything").header("Authorization", "Bearer " + login("a@b.com", "password123")))
				.andExpect(status().isForbidden());
	}
}
