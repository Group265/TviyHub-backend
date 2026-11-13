package team.project.TviyHub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import team.project.TviyHub.TestcontainersConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsArePublicAndDescribeEndpointsAndBearerAuth() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("TviyHub API"))
				.andExpect(jsonPath("$.paths['/api/auth/login']").exists())
				.andExpect(jsonPath("$.paths['/api/auth/register']").exists())
				.andExpect(jsonPath("$.paths['/api/auth/refresh']").exists())
				.andExpect(jsonPath("$.paths['/api/auth/logout']").exists())
				.andExpect(jsonPath("$.paths['/api/users/me']").exists())
				.andExpect(jsonPath("$.paths['/api/users/me/password/request']").exists())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
	}

	@Test
	void schemasHaveExamplesAndTheDescriptionExplainsTokenRefresh() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.description").value(org.hamcrest.Matchers.containsString("/api/auth/refresh")))
				.andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.phone.example").value("+380671234567"))
				.andExpect(jsonPath("$.components.schemas.AuthResponse.properties.refreshToken.description").isNotEmpty())
				.andExpect(jsonPath("$.components.schemas.ChangeRoleRequest.properties.role.example").value("ADMIN"))
				.andExpect(jsonPath("$.components.schemas.UserProfileResponse.properties.emailVerified.description").isNotEmpty());
	}

	@Test
	void eventSchemasHaveExamples() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/events']").exists())
				.andExpect(jsonPath("$.components.schemas.HostEventRequest.properties.title.example").value("Weekend Hackathon"))
				.andExpect(jsonPath("$.components.schemas.EventResponse.properties.freeSeats.description").isNotEmpty())
				.andExpect(jsonPath("$.components.schemas.EventRequestResponse.properties.status.description").isNotEmpty());
	}

	@Test
	void galleryEndpointsAreDocumented() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/gallery']").exists())
				.andExpect(jsonPath("$.paths['/api/gallery/{id}/image']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/gallery']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/gallery'].post.requestBody.content['multipart/form-data']").exists());
	}

	/**
	 * Guards against the docs falling behind the code for the auth, user, event and gallery endpoints:
	 * every operation has a summary, secured ones document 401, admin ones document 403.
	 */
	@Test
	@SuppressWarnings("unchecked")
	void operationsOfAuthUserEventAndGalleryAreDescribedConsistently() throws Exception {
		String body = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		java.util.Map<String, java.util.Map<String, java.util.Map<String, Object>>> paths =
				com.jayway.jsonpath.JsonPath.read(body, "$.paths");

		java.util.List<String> mine = java.util.List.of("/api/auth/", "/api/users/", "/api/admin/users", "/api/events",
				"/api/admin/events", "/api/admin/event-requests", "/api/admin/gallery", "/api/gallery");
		java.util.List<String> expectedPaths = java.util.List.of("/api/auth/google", "/api/auth/forgot-password",
				"/api/auth/reset-password", "/api/users/me/bookings", "/api/users/me/event-requests",
				"/api/admin/users/{id}/block", "/api/admin/event-requests");
		org.assertj.core.api.Assertions.assertThat(paths.keySet()).containsAll(expectedPaths);

		java.util.List<String> problems = new java.util.ArrayList<>();
		paths.forEach((path, operations) -> {
			if (mine.stream().noneMatch(path::startsWith)) {
				return;
			}
			operations.forEach((method, operation) -> {
				String name = method.toUpperCase() + " " + path;
				java.util.Map<String, Object> responses = (java.util.Map<String, Object>) operation.get("responses");
				if (operation.get("summary") == null) {
					problems.add(name + " has no summary");
				}
				if (operation.get("security") != null && !responses.containsKey("401")) {
					problems.add(name + " is secured but does not document 401");
				}
				if (path.startsWith("/api/admin") && !responses.containsKey("403")) {
					problems.add(name + " is for admins but does not document 403");
				}
			});
		});
		org.assertj.core.api.Assertions.assertThat(problems).isEmpty();
	}

	@Test
	void swaggerUiIsPublic() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}
}
