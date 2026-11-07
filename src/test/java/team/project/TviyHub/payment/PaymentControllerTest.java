package team.project.TviyHub.payment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import team.project.TviyHub.booking.Booking;
import team.project.TviyHub.booking.BookingRepository;
import team.project.TviyHub.payment.dto.PaymentUpdateDto;
import team.project.TviyHub.tariff.Tariff;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;
import team.project.TviyHub.workspace.Workspace;
import team.project.TviyHub.workspace.WorkspaceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    private User clientUser;
    private User adminUser;
    private Payment savedPayment;

    @BeforeEach
    void setUp() {
        // Очищаем таблицы в правильном порядке зависимостей
        bookingRepository.deleteAll();
        paymentRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Создаем пользователей по вашей схеме БД
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

        // 2. Создаем ворксейс
        Workspace testWorkspace = Workspace.builder()
                .name("Desk 1")
                .type(Workspace.WorkspaceType.DESK)
                .build();
        testWorkspace = workspaceRepository.save(testWorkspace);

        // 3. Собираем бронирование
        Booking booking = Booking.builder()
                .user(clientUser)
                .workspace(testWorkspace)
                .bookingType(Tariff.BookingType.HOURLY)
                .bookingDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(15, 0))
                .totalPrice(new BigDecimal("150.00"))
                .status(Booking.BookingStatus.CONFIRMED)
                .build();

        // 4. Создаем связанный платеж (используем Билдер с явным указанием CASH, как мы фиксили)
        Payment payment = Payment.builder()
                .status(Payment.PaymentStatus.UNPAID)
                .paymentMethod(Payment.PaymentMethod.CASH)
                .amount(new BigDecimal("150.00"))
                .booking(booking)
                .build();

        booking.setPayment(payment);

        // Каскадно сохраняем всю связку
        Booking savedBooking = bookingRepository.save(booking);
        savedPayment = savedBooking.getPayment();
    }

    @Test
    void adminShouldGetAllPaymentsSuccessfully() throws Exception {
        mockMvc.perform(get("/api/admin/payments")
                        .with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(savedPayment.getId()))
                .andExpect(jsonPath("$[0].status").value("UNPAID"))
                .andExpect(jsonPath("$[0].paymentMethod").value("CASH"))
                .andExpect(jsonPath("$[0].bookingId").value(savedPayment.getBooking().getId()));
    }

    @Test
    void adminShouldUpdatePaymentStatusToPaid() throws Exception {
        PaymentUpdateDto updateDto = new PaymentUpdateDto(
                Payment.PaymentStatus.PAID,
                Payment.PaymentMethod.CARD
        );

        mockMvc.perform(put("/api/admin/payments/{id}/status", savedPayment.getId())
                        .with(user(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty()); // Проверяем генерацию времени paidAt
    }

    @Test
    void clientShouldBeForbiddenToAccessAdminPayments() throws Exception {
        mockMvc.perform(get("/api/admin/payments")
                        .with(user(clientUser)))
                .andExpect(status().isForbidden()); // Обычный USER должен ловить 403 статус
    }

    @Test
    void adminShouldGet404WhenPaymentNotFound() throws Exception {
        PaymentUpdateDto updateDto = new PaymentUpdateDto(
                Payment.PaymentStatus.PAID,
                Payment.PaymentMethod.CASH
        );

        mockMvc.perform(put("/api/admin/payments/{id}/status", 99999L)
                        .with(user(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound()); // Ожидаем 404 на кастомный ResourceNotFoundException
    }
}
