package team.project.TviyHub.closedday;

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

import java.time.LocalDate;

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
class ClosedDayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClosedDayRepository closedDayRepository;

    private LocalDate targetDate;

    @BeforeEach
    void setUp() {
        closedDayRepository.deleteAll();
        targetDate = LocalDate.now().plusDays(5);
    }

    private static String closedDayBody(String date, String reason) {
        String dateValue = (date == null) ? "null" : "\"" + date + "\"";
        String reasonValue = (reason == null) ? "null" : "\"" + reason + "\"";

        return """
                {
                    "date": %s,
                    "reason": %s
                }
                """.formatted(dateValue, reasonValue);
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnEmptyListWhenNoClosedDaysExist() throws Exception {
        mockMvc.perform(get("/api/closed-days"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnTrueIfDateIsClosed() throws Exception {
        ClosedDay closedDay = ClosedDay.builder()
                .date(targetDate)
                .reason("Technical Work")
                .build();
        closedDayRepository.save(closedDay);

        mockMvc.perform(get("/api/closed-days/check")
                        .param("date", targetDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnForbiddenWhenUserTriesToCreateClosedDay() throws Exception {
        mockMvc.perform(post("/api/admin/closed-days")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closedDayBody(targetDate.toString(), "Staff Holiday")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminShouldCreateClosedDaySuccessfully() throws Exception {
        mockMvc.perform(post("/api/admin/closed-days")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closedDayBody(targetDate.toString(), "Repairs")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value(targetDate.toString()))
                .andExpect(jsonPath("$.reason").value("Repairs"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenReasonIsBlank() throws Exception {
        mockMvc.perform(post("/api/admin/closed-days")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closedDayBody(targetDate.toString(), "   ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenDateIsNull() throws Exception {
        mockMvc.perform(post("/api/admin/closed-days")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closedDayBody(null, "New Year Holiday")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminShouldUpdateClosedDayReason() throws Exception {
        ClosedDay closedDay = ClosedDay.builder().date(targetDate).reason("Old Reason").build();
        closedDayRepository.save(closedDay);

        mockMvc.perform(put("/api/admin/closed-days/{date}", targetDate.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closedDayBody(targetDate.toString(), "Updated Reason")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Updated Reason"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminShouldDeleteClosedDay() throws Exception {
        ClosedDay closedDay = ClosedDay.builder().date(targetDate).reason("To Be Removed").build();
        closedDayRepository.save(closedDay);

        mockMvc.perform(delete("/api/admin/closed-days/{date}", targetDate.toString()))
                .andExpect(status().isNoContent());
    }
}
