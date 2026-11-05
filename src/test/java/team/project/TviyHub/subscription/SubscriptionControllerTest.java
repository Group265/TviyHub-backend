package team.project.TviyHub.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.payment.Payment;
import team.project.TviyHub.subscription.dto.SubscriptionRequest;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;
import team.project.TviyHub.workspace.Workspace;
import team.project.TviyHub.workspace.WorkspaceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
public class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private User clientUser;
    private User adminUser;
    private Workspace deskWorkspace;
    private Workspace meetingRoomWorkspace;
    private Subscription existingSubscription;

    @BeforeEach
    void setUp() {
        subscriptionRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();

        clientUser = User.builder()
                .name("Client Name")
                .email("client@hub.com")
                .passwordHash("hashed_pass")
                .phone("+380501111111")
                .role(Role.USER)
                .build();
        clientUser = userRepository.save(clientUser);

        adminUser = User.builder()
                .name("Admin Name")
                .email("admin@hub.com")
                .passwordHash("hashed_pass")
                .phone("+380502222222")
                .role(Role.ADMIN)
                .build();
        adminUser = userRepository.save(adminUser);

        deskWorkspace = Workspace.builder()
                .name("Desk 1")
                .type(Workspace.WorkspaceType.DESK)
                .build();
        deskWorkspace = workspaceRepository.save(deskWorkspace);

        meetingRoomWorkspace = Workspace.builder()
                .name("Meeting room 1")
                .type(Workspace.WorkspaceType.MEETING_ROOM)
                .build();
        meetingRoomWorkspace = workspaceRepository.save(meetingRoomWorkspace);

        Payment activePayment = Payment.builder()
                .status(Payment.PaymentStatus.PAID)
                .amount(BigDecimal.valueOf(3500.00))
                .build();

        existingSubscription = Subscription.builder()
                .user(clientUser)
                .workspace(deskWorkspace)
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().plusDays(25))
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .payment(activePayment)
                .build();
        existingSubscription = subscriptionRepository.save(existingSubscription);
    }

    @Test
    void userShouldCreateSubscriptionSuccessfully() throws Exception {
        SubscriptionRequest request = new SubscriptionRequest(deskWorkspace.getId(), LocalDate.now().plusMonths(2));

        mockMvc.perform(post("/api/subscriptions")
                        .with(user(clientUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
    }

    @Test
    void shouldReturnConflictWhenSubscriptionIsOnMeetingRoom() throws Exception {
        SubscriptionRequest request = new SubscriptionRequest(meetingRoomWorkspace.getId(), LocalDate.now().plusMonths(1));

        mockMvc.perform(post("/api/subscriptions")
                        .with(user(clientUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void userShouldSeeOnlyHisOwnSubscriptions() throws Exception {
        mockMvc.perform(get("/api/subscriptions/my")
                        .with(user(clientUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(clientUser.getId()));
    }

    @Test
    void adminShouldSeeAllSubscriptionsGlobalList() throws Exception {
        mockMvc.perform(get("/api/admin/subscriptions")
                        .with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminShouldExtendActiveSubscriptionSuccessfully() throws Exception {
        LocalDate targetEndDate = existingSubscription.getEndDate().plusDays(10);

        mockMvc.perform(put("/api/admin/subscriptions/{id}/extend", existingSubscription.getId())
                        .with(user(adminUser))
                        .param("days", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value(targetEndDate.toString()));
    }

    @Test
    void adminShouldCancelSubscriptionAndTriggerRefund() throws Exception {
        mockMvc.perform(delete("/api/admin/subscriptions/{id}", existingSubscription.getId())
                        .with(user("admin@hub.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
    }
}
