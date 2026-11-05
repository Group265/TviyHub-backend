package team.project.TviyHub.tariff;

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
import team.project.TviyHub.workspace.Workspace;

import java.math.BigDecimal;

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
public class TariffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TariffRepository tariffRepository;

    private Tariff existingTariff;

    @BeforeEach
    void setUp() {
        tariffRepository.deleteAll();

        existingTariff = Tariff.builder()
                .workspaceType(Workspace.WorkspaceType.DESK)
                .bookingType(Tariff.BookingType.HOURLY)
                .price(new BigDecimal("150.00"))
                .build();
        existingTariff = tariffRepository.save(existingTariff);
    }

    private static String tariffBody(String workspaceType, String bookingType, String price) {
        String workspaceValue = (workspaceType == null) ? "null" : "\"" + workspaceType + "\"";
        String bookingValue = (bookingType == null) ? "null" : "\"" + bookingType + "\"";
        String priceValue = (price == null) ? "null" : price;

        return """
                {
                    "workspaceType": %s,
                    "bookingType": %s,
                    "price": %s
                }
                """.formatted(workspaceValue, bookingValue, priceValue);
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnPublicPriceList() throws Exception {
        mockMvc.perform(get("/api/tariffs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].workspaceType").value("DESK"))
                .andExpect(jsonPath("$[0].bookingType").value("HOURLY"))
                .andExpect(jsonPath("$[0].price").value(150.00));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnTariffById() throws Exception {
        mockMvc.perform(get("/api/tariffs/{id}", existingTariff.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingTariff.getId()))
                .andExpect(jsonPath("$.workspaceType").value("DESK"))
                .andExpect(jsonPath("$.bookingType").value("HOURLY"))
                .andExpect(jsonPath("$.price").value(150.00));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnNotFoundWhenTariffDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/tariffs/{id}", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnForbiddenWhenUserTriesToCreateTariff() throws Exception {
        mockMvc.perform(post("/api/admin/tariffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tariffBody("MEETING_ROOM", "DAILY", "500.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminShouldCreateTariffSuccessfully() throws Exception {
        mockMvc.perform(post("/api/admin/tariffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tariffBody("MEETING_ROOM", "HOURLY", "500.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workspaceType").value("MEETING_ROOM"))
                .andExpect(jsonPath("$.bookingType").value("HOURLY"))
                .andExpect(jsonPath("$.price").value(500.00));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnConflictWhenTariffAlreadyExists() throws Exception {
        mockMvc.perform(post("/api/admin/tariffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tariffBody("DESK", "HOURLY", "200.00")))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenFieldsAreNull() throws Exception {
        mockMvc.perform(post("/api/admin/tariffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tariffBody(null, null, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenPriceIsNegative() throws Exception {
        mockMvc.perform(post("/api/admin/tariffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tariffBody("DESK", "SUBSCRIPTION", "-10.50")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminShouldUpdateTariffSuccessfully() throws Exception {
        mockMvc.perform(put("/api/admin/tariffs/{id}", existingTariff.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tariffBody("DESK", "HOURLY", "180.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(180.00));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnConflictWhenUpdatingToExistingPair() throws Exception {
        Tariff anotherTariff = Tariff.builder()
                .workspaceType(Workspace.WorkspaceType.DESK)
                .bookingType(Tariff.BookingType.DAILY)
                .price(new BigDecimal("400.00"))
                .build();
        tariffRepository.save(anotherTariff);

        mockMvc.perform(put("/api/admin/tariffs/{id}", existingTariff.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tariffBody("DESK", "DAILY", "450.00")))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnForbiddenWhenUserTriesToDeleteTariff() throws Exception {
        mockMvc.perform(delete("/api/admin/tariffs/{id}", existingTariff.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminShouldDeleteTariffSuccessfully() throws Exception {
        mockMvc.perform(delete("/api/admin/tariffs/{id}", existingTariff.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tariffs/{id}", existingTariff.getId()))
                .andExpect(status().isNotFound());
    }
}
