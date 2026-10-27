package team.project.TviyHub.user;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerTest {

	private static final String PASSWORD = "password123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private User admin;
	private User user;
	private String adminToken;
	private String userToken;

	@BeforeEach
	void setUp() throws Exception {
		userRepository.deleteAll();
		admin = save("Admin", "+380500000001", "admin@b.com", Role.ADMIN);
		user = save("Regular User", "+380500000002", "user@b.com", Role.USER);
		adminToken = login("admin@b.com", PASSWORD);
		userToken = login("user@b.com", PASSWORD);
	}

	private User save(String name, String phone, String email, Role role) {
		User u = new User();
		u.setName(name);
		u.setPhone(phone);
		u.setEmail(email);
		u.setPasswordHash(passwordEncoder.encode(PASSWORD));
		u.setRole(role);
		return userRepository.saveAndFlush(u);
	}

	private String login(String email, String password) throws Exception {
		String response = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	private ResultActions asAdmin(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
			throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + adminToken));
	}

	private ResultActions asAdminJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
									  String json) throws Exception {
		return asAdmin(request.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@Test
	void adminAreaRequiresAdminRole() throws Exception {
		mockMvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + userToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/users/" + user.getId() + "/block").header("Authorization", "Bearer " + userToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void listsUsersWithPaginationAndFilters() throws Exception {
		save("Third", "+380500000003", "third@b.com", Role.USER);

		asAdmin(get("/admin/users?size=2&sortBy=id&direction=asc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content[0].email").value("admin@b.com"));

		asAdmin(get("/admin/users?role=ADMIN"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].role").value("ADMIN"));

		asAdmin(get("/admin/users?search=THIRD"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].email").value("third@b.com"));

		asAdmin(get("/admin/users").param("search", "+380500000002"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].email").value("user@b.com"));

		asAdmin(get("/admin/users?blocked=true")).andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void searchTreatsLikeWildcardsLiterally() throws Exception {
		asAdmin(get("/admin/users").param("search", "%")).andExpect(jsonPath("$.totalElements").value(0));
		asAdmin(get("/admin/users").param("search", "_")).andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void rejectsUnknownSortField() throws Exception {
		asAdmin(get("/admin/users?sortBy=passwordHash")).andExpect(status().isBadRequest());
	}

	@Test
	void getsUserByIdAndReturns404ForUnknown() throws Exception {
		asAdmin(get("/admin/users/" + user.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("user@b.com"))
				.andExpect(jsonPath("$.blocked").value(false))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
		asAdmin(get("/admin/users/999999")).andExpect(status().isNotFound());
	}

	@Test
	void returnsStats() throws Exception {
		asAdmin(get("/admin/users/stats"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(2))
				.andExpect(jsonPath("$.admins").value(1))
				.andExpect(jsonPath("$.blocked").value(0));
	}

	@Test
	void createsUserWithRoleAndNewAdminCanLogIn() throws Exception {
		asAdminJson(post("/admin/users"), """
				{"name":"New Admin","phone":"+380500000010","email":"New.Admin@b.com","password":"password123","role":"ADMIN"}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("new.admin@b.com"))
				.andExpect(jsonPath("$.role").value("ADMIN"));

		String token = login("new.admin@b.com", PASSWORD);
		mockMvc.perform(get("/admin/users/stats").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
	}

	@Test
	void rejectsCreateWithDuplicateOrInvalidData() throws Exception {
		asAdminJson(post("/admin/users"), """
				{"name":"Dup","phone":"+380500000011","email":"user@b.com","password":"password123","role":"USER"}
				""").andExpect(status().isConflict());
		asAdminJson(post("/admin/users"), """
				{"name":"Dup","phone":"+380500000002","email":"dup@b.com","password":"password123","role":"USER"}
				""").andExpect(status().isConflict());
		asAdminJson(post("/admin/users"), """
				{"name":"Bad","phone":"+380500000012","email":"bad@b.com","password":"short","role":"USER"}
				""").andExpect(status().isBadRequest());
		asAdminJson(post("/admin/users"), """
				{"name":"NoRole","phone":"+380500000013","email":"norole@b.com","password":"password123"}
				""").andExpect(status().isBadRequest());
	}

	@Test
	void updatesUserPartiallyAndChecksConflicts() throws Exception {
		asAdminJson(patch("/admin/users/" + user.getId()), "{\"name\":\"Renamed\",\"email\":\"Renamed@b.com\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Renamed"))
				.andExpect(jsonPath("$.email").value("renamed@b.com"))
				.andExpect(jsonPath("$.phone").value("+380500000002"));

		asAdminJson(patch("/admin/users/" + user.getId()), "{\"email\":\"admin@b.com\"}")
				.andExpect(status().isConflict());
		asAdminJson(patch("/admin/users/" + user.getId()), "{\"phone\":\"+380500000001\"}")
				.andExpect(status().isConflict());
		asAdminJson(patch("/admin/users/" + user.getId()), "{\"name\":\"  \"}")
				.andExpect(status().isBadRequest());
		asAdminJson(patch("/admin/users/999999"), "{\"name\":\"x\"}").andExpect(status().isNotFound());
	}

	@Test
	void changesRoleWithImmediateEffectOnExistingToken() throws Exception {
		mockMvc.perform(get("/admin/users/stats").header("Authorization", "Bearer " + userToken))
				.andExpect(status().isForbidden());

		asAdminJson(patch("/admin/users/" + user.getId() + "/role"), "{\"role\":\"ADMIN\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));

		mockMvc.perform(get("/admin/users/stats").header("Authorization", "Bearer " + userToken))
				.andExpect(status().isOk());
	}

	@Test
	void adminCannotChangeOwnRoleOrBlockThemselves() throws Exception {
		asAdminJson(patch("/admin/users/" + admin.getId() + "/role"), "{\"role\":\"USER\"}")
				.andExpect(status().isBadRequest());
		asAdmin(post("/admin/users/" + admin.getId() + "/block")).andExpect(status().isBadRequest());
		asAdminJson(patch("/admin/users/" + user.getId() + "/role"), "{}").andExpect(status().isBadRequest());
	}

	@Test
	void blockedUserLosesAccessImmediatelyAndCanBeUnblocked() throws Exception {
		asAdmin(post("/admin/users/" + user.getId() + "/block"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blocked").value(true));

		mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + userToken))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"user@b.com\",\"password\":\"password123\"}"))
				.andExpect(status().isForbidden());
		asAdmin(get("/admin/users/stats")).andExpect(jsonPath("$.blocked").value(1));
		asAdmin(get("/admin/users?blocked=true")).andExpect(jsonPath("$.totalElements").value(1));

		asAdmin(post("/admin/users/" + user.getId() + "/unblock"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blocked").value(false));
		mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + userToken))
				.andExpect(status().isOk());
		login("user@b.com", PASSWORD);
	}

	@Test
	void resetsPassword() throws Exception {
		asAdminJson(post("/admin/users/" + user.getId() + "/reset-password"), "{\"newPassword\":\"brandnew456\"}")
				.andExpect(status().isNoContent());

		login("user@b.com", "brandnew456");
		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"user@b.com\",\"password\":\"password123\"}"))
				.andExpect(status().isUnauthorized());

		asAdminJson(post("/admin/users/" + user.getId() + "/reset-password"), "{\"newPassword\":\"short\"}")
				.andExpect(status().isBadRequest());
		asAdminJson(post("/admin/users/999999/reset-password"), "{\"newPassword\":\"brandnew456\"}")
				.andExpect(status().isNotFound());
	}
}
