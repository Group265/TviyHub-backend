package team.project.TviyHub.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.booking.dto.BookingRequestDto;
import team.project.TviyHub.closedday.ClosedDayService;
import team.project.TviyHub.event.Event;
import team.project.TviyHub.event.EventRepository;
import team.project.TviyHub.payment.Payment;
import team.project.TviyHub.tariff.Tariff;
import team.project.TviyHub.tariff.TariffRepository;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;
import team.project.TviyHub.workspace.Workspace;
import team.project.TviyHub.workspace.WorkspaceRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
public class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private TariffRepository tariffRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @MockitoBean
    private ClosedDayService closedDayService;

    private User testUser;
    private User adminUser;
    private Workspace testWorkspace;
    private Tariff hourlyTariff;
    private Booking existingBooking;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        workspaceRepository.deleteAll();
        tariffRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
                .name("Test User")
                .email("user@hub.com")
                .passwordHash("password")
                .phone("+380501234567")
                .role(Role.USER)
                .build();
        testUser = userRepository.save(testUser);

        adminUser = User.builder()
                .name("Admin User")
                .email("admin@hub.com")
                .passwordHash("password")
                .phone("+380501234568")
                .role(Role.ADMIN)
                .build();
        adminUser = userRepository.save(adminUser);

        testWorkspace = Workspace.builder()
                .name("Робоче місце №1")
                .type(Workspace.WorkspaceType.DESK)
                .build();
        testWorkspace = workspaceRepository.save(testWorkspace);

        hourlyTariff = Tariff.builder()
                .workspaceType(Workspace.WorkspaceType.DESK)
                .bookingType(Tariff.BookingType.HOURLY)
                .price(new BigDecimal("150.00"))
                .build();
        tariffRepository.save(hourlyTariff);

        LocalDate futureDate = LocalDate.now().plusWeeks(1);
        while (futureDate.getDayOfWeek().getValue() > 5) {
            futureDate = futureDate.plusDays(1);
        }
        existingBooking = Booking.builder()
                .user(testUser)
                .workspace(testWorkspace)
                .bookingType(Tariff.BookingType.HOURLY)
                .bookingDate(futureDate)
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .totalPrice(new BigDecimal("150.00"))
                .status(Booking.BookingStatus.CONFIRMED)
                .build();
        Payment testPayment = Payment.builder()
                .status(Payment.PaymentStatus.UNPAID)
                .amount(new BigDecimal("150.00"))
                .booking(existingBooking)
                .build();
        existingBooking.setPayment(testPayment);

        existingBooking = bookingRepository.save(existingBooking);

        when(closedDayService.isDateClosed(any())).thenReturn(false);
    }

    @Test
    void userShouldSeeOnlyOwnBookings() throws Exception {
        mockMvc.perform(get("/api/bookings").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(testUser.getId()));
    }

    @Test
    void adminShouldSeeAllBookings() throws Exception {
        mockMvc.perform(get("/api/bookings").with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldReturnAvailabilityMap() throws Exception {
        LocalDate checkDate = LocalDate.now().plusWeeks(1);
        mockMvc.perform(get("/api/bookings/availability")
                        .with(user("user@hub.com").roles("USER"))
                        .param("date", checkDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void userShouldCreateHourlyBookingSuccessfully() throws Exception {
        LocalDate targetDate = LocalDate.now().plusWeeks(1);
        while (targetDate.getDayOfWeek().getValue() > 5) {
            targetDate = targetDate.plusDays(1);
        }

        BookingRequestDto request = new BookingRequestDto(
                testWorkspace.getId(),
                Tariff.BookingType.HOURLY,
                List.of(targetDate),
                LocalTime.of(14, 0),
                LocalTime.of(15, 0)
        );

        mockMvc.perform(post("/api/bookings")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].bookingType").value("HOURLY"))
                .andExpect(jsonPath("$[0].totalPrice").value(150.00));
    }

    @Test
    void shouldReturnBadRequestWhenMinutesAreNotZero() throws Exception {
        BookingRequestDto invalidRequest = new BookingRequestDto(
                testWorkspace.getId(),
                Tariff.BookingType.HOURLY,
                List.of(LocalDate.now().plusDays(2)),
                LocalTime.of(14, 15),
                LocalTime.of(15, 15)
        );

        mockMvc.perform(post("/api/bookings")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnConflictWhenBookingOverlaps() throws Exception {
        BookingRequestDto overlapRequest = new BookingRequestDto(
                testWorkspace.getId(),
                Tariff.BookingType.HOURLY,
                List.of(existingBooking.getBookingDate()),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0)
        );

        mockMvc.perform(post("/api/bookings")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overlapRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnConflictWhenBookingOnDayWithScheduledEvent() throws Exception {
        LocalDate eventDate = LocalDate.now().plusWeeks(1);
        while (eventDate.getDayOfWeek().getValue() > 5) {
            eventDate = eventDate.plusDays(1);
        }

        Event scheduledEvent = new team.project.TviyHub.event.Event();
        scheduledEvent.setTitle("IT Conference");
        scheduledEvent.setDescription("Event day");
        scheduledEvent.setEventDate(eventDate);
        scheduledEvent.setStartTime(LocalTime.of(9, 0));
        scheduledEvent.setEndTime(LocalTime.of(18, 0));
        scheduledEvent.setCreatedAt(Instant.now());

        eventRepository.saveAndFlush(scheduledEvent);

        BookingRequestDto conflictRequest = new BookingRequestDto(
                testWorkspace.getId(),
                Tariff.BookingType.HOURLY,
                List.of(eventDate),
                LocalTime.of(14, 0),
                LocalTime.of(15, 0)
        );

        mockMvc.perform(post("/api/bookings")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictRequest)))
                .andExpect(status().isConflict());
    }


    @Test
    void shouldCalculateEstimatedPrice() throws Exception {
        BookingRequestDto priceRequest = new BookingRequestDto(
                testWorkspace.getId(),
                Tariff.BookingType.HOURLY,
                List.of(LocalDate.now().plusDays(2)),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0) // 2 часа
        );

        mockMvc.perform(post("/api/bookings/calculate-price")
                        .with(user("user@hub.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(priceRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string("300.00")); // 150 * 2 часа
    }

    @Test
    void adminShouldCancelAnyBookingAtAnyTime() throws Exception {
        mockMvc.perform(patch("/api/bookings/{id}/cancel", existingBooking.getId())
                        .with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void userCannotCancelOtherUsersBooking() throws Exception {
        User anotherUser = User.builder()
                .name("Other User")
                .email("other@hub.com")
                .passwordHash("password")
                .phone("+380501234569")
                .role(Role.USER)
                .build();
        anotherUser = userRepository.save(anotherUser);

        mockMvc.perform(patch("/api/bookings/{id}/cancel", existingBooking.getId())
                        .with(user(anotherUser)))
                .andExpect(status().isForbidden());
    }
}
