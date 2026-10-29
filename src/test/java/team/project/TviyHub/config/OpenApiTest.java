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
				.andExpect(jsonPath("$.paths['/auth/login']").exists())
				.andExpect(jsonPath("$.paths['/auth/register']").exists())
				.andExpect(jsonPath("$.paths['/auth/refresh']").exists())
				.andExpect(jsonPath("$.paths['/auth/logout']").exists())
				.andExpect(jsonPath("$.paths['/users/me']").exists())
				.andExpect(jsonPath("$.paths['/users/me/password/request']").exists())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
	}

	@Test
	void swaggerUiIsPublic() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}
}
