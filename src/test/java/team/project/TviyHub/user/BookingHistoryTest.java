package team.project.TviyHub.user;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
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
import team.project.TviyHub.booking.Booking;
import team.project.TviyHub.booking.BookingRepository;
import team.project.TviyHub.payment.Payment;
import team.project.TviyHub.tariff.Tariff;
import team.project.TviyHub.workspace.Workspace;
import team.project.TviyHub.workspace.WorkspaceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookingHistoryTest {

	private static final String PASSWORD = "password123";
	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Kyiv"));

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private WorkspaceRepository workspaceRepository;
	@Autowired
	private BookingRepository bookingRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;

	private User alice;
	private User bob;
	private Workspace desk;
	private Workspace room;
	private String aliceToken;

	@BeforeEach
	void setUp() throws Exception {
		cleanUp();
		alice = saveUser("Alice", "+380500000002", "alice@b.com");
		bob = saveUser("Bob", "+380500000003", "bob@b.com");
		desk = workspaceRepository.save(Workspace.builder().name("Desk 1").type(Workspace.WorkspaceType.DESK).build());
		room = workspaceRepository.save(Workspace.builder().name("Room A").type(Workspace.WorkspaceType.MEETING_ROOM).build());
		aliceToken = login("alice@b.com");
	}

	@AfterEach
	void cleanUp() {
		bookingRepository.deleteAll();
		workspaceRepository.deleteAll();
		userRepository.deleteAll();
	}

	private User saveUser(String name, String phone, String email) {
		User user = new User();
		user.setName(name);
		user.setPhone(phone);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		user.setRole(Role.USER);
		user.setEmailVerified(true);
		return userRepository.saveAndFlush(user);
	}

	private String login(String email) throws Exception {
		String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	private Booking book(User user, Workspace workspace, LocalDate date, LocalTime start,
						 Booking.BookingStatus status) {
		Booking booking = Booking.builder()
				.user(user).workspace(workspace)
				.bookingType(start == null ? Tariff.BookingType.DAILY : Tariff.BookingType.HOURLY)
				.bookingDate(date).startTime(start).endTime(start == null ? null : start.plusHours(2))
				.totalPrice(new BigDecimal("300.00")).status(status)
				.build();
		booking.setPayment(Payment.builder().status(Payment.PaymentStatus.UNPAID)
				.amount(new BigDecimal("300.00")).booking(booking).build());
		return bookingRepository.saveAndFlush(booking);
	}

	private ResultActions history(String query) throws Exception {
		return mockMvc.perform(get("/api/users/me/bookings" + query).header("Authorization", "Bearer " + aliceToken));
	}

	@Test
	void showsDateTimeAndPlaceOfOwnBookingsOnly() throws Exception {
		book(alice, desk, TODAY.plusDays(3), LocalTime.of(10, 0), Booking.BookingStatus.CONFIRMED);
		book(bob, desk, TODAY.plusDays(4), LocalTime.of(10, 0), Booking.BookingStatus.CONFIRMED);

		history("")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].bookingDate").value(TODAY.plusDays(3).toString()))
				.andExpect(jsonPath("$.content[0].startTime").value("10:00:00"))
				.andExpect(jsonPath("$.content[0].endTime").value("12:00:00"))
				.andExpect(jsonPath("$.content[0].bookingType").value("HOURLY"))
				.andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
				.andExpect(jsonPath("$.content[0].totalPrice").value(300.00))
				.andExpect(jsonPath("$.content[0].workspace.name").value("Desk 1"))
				.andExpect(jsonPath("$.content[0].workspace.type").value("DESK"));
	}

	@Test
	void allAndPastNewestFirstUpcomingNearestFirst() throws Exception {
		book(alice, desk, TODAY.minusDays(10), LocalTime.of(9, 0), Booking.BookingStatus.COMPLETED);
		book(alice, room, TODAY.minusDays(2), LocalTime.of(9, 0), Booking.BookingStatus.CANCELLED);
		book(alice, desk, TODAY.plusDays(1), null, Booking.BookingStatus.CONFIRMED);
		book(alice, room, TODAY.plusDays(7), LocalTime.of(14, 0), Booking.BookingStatus.CONFIRMED);

		history("?scope=all")
				.andExpect(jsonPath("$.totalElements").value(4))
				.andExpect(jsonPath("$.content[0].bookingDate").value(TODAY.plusDays(7).toString()))
				.andExpect(jsonPath("$.content[3].bookingDate").value(TODAY.minusDays(10).toString()));

		history("?scope=past")
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].bookingDate").value(TODAY.minusDays(2).toString()))
				.andExpect(jsonPath("$.content[0].status").value("CANCELLED"))
				.andExpect(jsonPath("$.content[0].workspace.type").value("MEETING_ROOM"))
				.andExpect(jsonPath("$.content[1].bookingDate").value(TODAY.minusDays(10).toString()));

		history("?scope=upcoming")
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].bookingDate").value(TODAY.plusDays(1).toString()))
				.andExpect(jsonPath("$.content[0].startTime").doesNotExist())
				.andExpect(jsonPath("$.content[0].bookingType").value("DAILY"))
				.andExpect(jsonPath("$.content[1].bookingDate").value(TODAY.plusDays(7).toString()));
	}

	@Test
	void todaysBookingCountsAsUpcoming() throws Exception {
		book(alice, desk, TODAY, LocalTime.of(10, 0), Booking.BookingStatus.CONFIRMED);

		history("?scope=upcoming").andExpect(jsonPath("$.totalElements").value(1));
		history("?scope=past").andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void pagesTheHistory() throws Exception {
		for (int i = 1; i <= 3; i++) {
			book(alice, desk, TODAY.minusDays(i), LocalTime.of(9, 0), Booking.BookingStatus.COMPLETED);
		}

		history("?page=0&size=2")
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2));
		history("?page=1&size=2")
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].bookingDate").value(TODAY.minusDays(3).toString()));
	}

	@Test
	void emptyHistoryIsAnEmptyPage() throws Exception {
		history("").andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void requiresLoginAndValidScope() throws Exception {
		mockMvc.perform(get("/api/users/me/bookings")).andExpect(status().isUnauthorized());
		history("?scope=nonsense").andExpect(status().isBadRequest());
	}
}
