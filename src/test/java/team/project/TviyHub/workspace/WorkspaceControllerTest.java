package team.project.TviyHub.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.TestcontainersConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
public class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private Workspace existingWorkspace;

    @BeforeEach
    void setUp() {
        workspaceRepository.deleteAll();

        existingWorkspace = Workspace.builder()
                .name("Workspace 1")
                .type(Workspace.WorkspaceType.DESK)
                .build();
        existingWorkspace = workspaceRepository.save(existingWorkspace);
    }

    private static String workspaceBody(String name, String type) {
        String nameValue = (name == null) ? "null" : "\"" + name + "\"";
        String typeValue = (type == null) ? "null" : "\"" + type + "\"";

        return """
                {
                    "name": %s,
                    "type": %s
                }
                """.formatted(nameValue, typeValue);
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnPublicHallLayout() throws Exception {
        mockMvc.perform(get("/api/workspaces"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Workspace 1"))
                .andExpect(jsonPath("$[0].type").value("DESK"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnWorkspaceById() throws Exception {
        mockMvc.perform(get("/api/workspaces/{id}", existingWorkspace.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingWorkspace.getId()))
                .andExpect(jsonPath("$.name").value("Workspace 1"))
                .andExpect(jsonPath("$.type").value("DESK"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnNotFoundWhenWorkspaceDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/workspaces/{id}", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnForbiddenWhenUserTriesToCreateWorkspace() throws Exception {
        mockMvc.perform(post("/api/admin/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workspaceBody("Meeting Room", "MEETING_ROOM")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminShouldCreateWorkspaceSuccessfully() throws Exception {
        mockMvc.perform(post("/api/admin/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workspaceBody("Meeting Room", "MEETING_ROOM")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Meeting Room"))
                .andExpect(jsonPath("$.type").value("MEETING_ROOM"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/admin/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workspaceBody("   ", "DESK")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenTypeIsNull() throws Exception {
        mockMvc.perform(post("/api/admin/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workspaceBody("New space", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminShouldUpdateWorkspaceSuccessfully() throws Exception {
        mockMvc.perform(put("/api/admin/workspaces/{id}", existingWorkspace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workspaceBody("Updated workspace", "DESK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated workspace"))
                .andExpect(jsonPath("$.type").value("DESK"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnForbiddenWhenUserTriesToDeleteWorkspace() throws Exception {
        mockMvc.perform(delete("/api/admin/workspaces/{id}", existingWorkspace.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminShouldDeleteWorkspaceSuccessfully() throws Exception {
        mockMvc.perform(delete("/api/admin/workspaces/{id}", existingWorkspace.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/workspaces/{id}", existingWorkspace.getId()))
                .andExpect(status().isNotFound());
    }

}
