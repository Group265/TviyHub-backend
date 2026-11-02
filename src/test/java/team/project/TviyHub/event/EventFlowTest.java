package team.project.TviyHub.event;

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
import team.project.TviyHub.TestMailConfig;
import team.project.TviyHub.TestMailConfig.CapturingEmailService;
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.booking.Booking;
import team.project.TviyHub.booking.BookingRepository;
import team.project.TviyHub.closedday.ClosedDay;
import team.project.TviyHub.closedday.ClosedDayRepository;
import team.project.TviyHub.tariff.Tariff;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;
import team.project.TviyHub.workspace.WorkspaceRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfig.class, TestMailConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class EventFlowTest {

	private static final String PASSWORD = "password123";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private EventRepository eventRepository;
	@Autowired
	private EventRequestRepository requestRepository;
	@Autowired
	private BookingRepository bookingRepository;
	@Autowired
	private ClosedDayRepository closedDayRepository;
	@Autowired
	private WorkspaceRepository workspaceRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private CapturingEmailService mail;

	private final LocalDate saturday = LocalDate.now(ZoneOffset.UTC)
			.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).plusWeeks(1);
	private final LocalDate sunday = saturday.plusDays(1);

	private User admin;
	private User alice;
	private User bob;
	private String adminToken;
	private String aliceToken;
	private String bobToken;

	@BeforeEach
	void setUp() throws Exception {
		cleanUp();
		mail.reset();
		admin = saveUser("Admin", "+380500000001", "admin@b.com", Role.ADMIN, true);
		alice = saveUser("Alice", "+380500000002", "alice@b.com", Role.USER, true);
		bob = saveUser("Bob", "+380500000003", "bob@b.com", Role.USER, true);
		adminToken = login("admin@b.com");
		aliceToken = login("alice@b.com");
		bobToken = login("bob@b.com");
	}

	@AfterEach
	void cleanUp() {
		requestRepository.deleteAll();
		eventRepository.deleteAll();
		bookingRepository.deleteAll();
		closedDayRepository.deleteAll();
		userRepository.deleteAll();
	}

	private User saveUser(String name, String phone, String email, Role role, boolean verified) {
		User user = new User();
		user.setName(name);
		user.setPhone(phone);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		user.setRole(role);
		user.setEmailVerified(verified);
		return userRepository.saveAndFlush(user);
	}

	private String login(String email) throws Exception {
		String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	private ResultActions postJson(String token, String path, String json) throws Exception {
		var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json);
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return mockMvc.perform(request);
	}

	private ResultActions getAs(String token, String path) throws Exception {
		return mockMvc.perform(get(path).header("Authorization", "Bearer " + token));
	}

	private String hostBody(LocalDate date, Integer capacity) {
		return """
				{"title":"Hackathon","description":"A weekend hackathon","date":"%s","startTime":"10:00","endTime":"18:00","capacity":%s,"comment":"Please"}
				""".formatted(date, capacity);
	}

	private long submitHost(String token, LocalDate date, Integer capacity) throws Exception {
		String response = postJson(token, "/api/events/host-requests", hostBody(date, capacity))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("NEW"))
				.andExpect(jsonPath("$.type").value("HOST"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private ResultActions approve(long requestId) throws Exception {
		return postJson(adminToken, "/api/admin/event-requests/" + requestId + "/approve", "");
	}

	/** Creates an approved event hosted by Alice and returns its id. */
	private long approvedEvent(LocalDate date, Integer capacity) throws Exception {
		long requestId = submitHost(aliceToken, date, capacity);
		String response = approve(requestId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.eventId")).longValue();
	}

	private long submitAttend(String token, long eventId) throws Exception {
		String response = postJson(token, "/api/events/" + eventId + "/attend-requests", "{\"comment\":\"I want to join\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("ATTEND"))
				.andExpect(jsonPath("$.status").value("NEW"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	// ---- host requests ----

	@Test
	void approvedHostRequestBecomesAnEventWithTheRequesterAsOrganizer() throws Exception {
		long requestId = submitHost(aliceToken, saturday, 20);

		approve(requestId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.eventId").isNotEmpty());

		mockMvc.perform(get("/api/events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].title").value("Hackathon"))
				.andExpect(jsonPath("$[0].date").value(saturday.toString()))
				.andExpect(jsonPath("$[0].hostName").value("Alice"))
				.andExpect(jsonPath("$[0].capacity").value(20))
				.andExpect(jsonPath("$[0].freeSeats").value(20))
				.andExpect(jsonPath("$[0].past").value(false));
		assertThat(mail.sentCount("alice@b.com")).isEqualTo(1);
	}

	@Test
	void rejectedHostRequestCreatesNoEventAndExplainsWhy() throws Exception {
		long requestId = submitHost(aliceToken, saturday, null);

		postJson(adminToken, "/api/admin/event-requests/" + requestId + "/reject", "{\"reason\":\"Hub is busy\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.reviewComment").value("Hub is busy"));

		mockMvc.perform(get("/api/events")).andExpect(jsonPath("$").isEmpty());
		getAs(aliceToken, "/api/users/me/event-requests")
				.andExpect(jsonPath("$[0].status").value("REJECTED"));
		assertThat(mail.sentCount("alice@b.com")).isEqualTo(1);
	}

	@Test
	void hostRequestNeedsAFutureWeekendAndValidTimes() throws Exception {
		LocalDate monday = saturday.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
		postJson(aliceToken, "/api/events/host-requests", hostBody(monday, 10)).andExpect(status().isBadRequest());

		LocalDate pastSaturday = LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previous(DayOfWeek.SATURDAY));
		postJson(aliceToken, "/api/events/host-requests", hostBody(pastSaturday, 10)).andExpect(status().isBadRequest());

		postJson(aliceToken, "/api/events/host-requests", """
				{"title":"X","description":"Y","date":"%s","startTime":"18:00","endTime":"10:00"}
				""".formatted(saturday)).andExpect(status().isBadRequest());
		postJson(aliceToken, "/api/events/host-requests", """
				{"title":"","description":"Y","date":"%s","startTime":"10:00","endTime":"12:00"}
				""".formatted(saturday)).andExpect(status().isBadRequest());
	}

	@Test
	void hostRequestIsRefusedWhenTheDateIsTakenClosedOrBooked() throws Exception {
		approvedEvent(saturday, null);
		postJson(bobToken, "/api/events/host-requests", hostBody(saturday, 5)).andExpect(status().isConflict());

		closedDayRepository.saveAndFlush(ClosedDay.builder().date(sunday).reason("Repair").build());
		postJson(bobToken, "/api/events/host-requests", hostBody(sunday, 5)).andExpect(status().isConflict());

		LocalDate nextSaturday = saturday.plusWeeks(1);
		bookingRepository.saveAndFlush(Booking.builder()
				.user(bob)
				.workspace(workspaceRepository.findAll().get(0))
				.bookingType(Tariff.BookingType.DAILY)
				.bookingDate(nextSaturday)
				.startTime(LocalTime.of(9, 0))
				.endTime(LocalTime.of(18, 0))
				.totalPrice(BigDecimal.TEN)
				.status(Booking.BookingStatus.CONFIRMED)
				.build());
		postJson(bobToken, "/api/events/host-requests", hostBody(nextSaturday, 5)).andExpect(status().isConflict());
	}

	@Test
	void secondRequestForTheSameDateFailsOnApproval() throws Exception {
		long first = submitHost(aliceToken, saturday, null);
		long second = submitHost(bobToken, saturday, null);

		approve(first).andExpect(status().isOk());
		approve(second).andExpect(status().isConflict());
	}

	// ---- attend requests ----

	@Test
	void attendRequestIsApprovedAndShownOnTheEventCard() throws Exception {
		long eventId = approvedEvent(saturday, 2);
		long requestId = submitAttend(bobToken, eventId);

		mockMvc.perform(get("/api/events/" + eventId))
				.andExpect(jsonPath("$.approvedParticipants").value(0))
				.andExpect(jsonPath("$.freeSeats").value(2));

		approve(requestId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

		mockMvc.perform(get("/api/events/" + eventId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.approvedParticipants").value(1))
				.andExpect(jsonPath("$.freeSeats").value(1));
		getAs(adminToken, "/api/admin/events/" + eventId + "/requests")
				.andExpect(jsonPath("$[0].userName").value("Bob"))
				.andExpect(jsonPath("$[0].status").value("APPROVED"));
	}

	@Test
	void cannotApproveMoreParticipantsThanSeats() throws Exception {
		long eventId = approvedEvent(saturday, 1);
		User carol = saveUser("Carol", "+380500000004", "carol@b.com", Role.USER, true);
		long first = submitAttend(bobToken, eventId);
		long second = submitAttend(login(carol.getEmail()), eventId);

		approve(first).andExpect(status().isOk());
		approve(second).andExpect(status().isConflict());
	}

	@Test
	void duplicateOrOwnOrPastAttendRequestsAreRefused() throws Exception {
		long eventId = approvedEvent(saturday, null);
		submitAttend(bobToken, eventId);
		postJson(bobToken, "/api/events/" + eventId + "/attend-requests", "{}").andExpect(status().isConflict());

		// the organizer cannot attend their own event
		postJson(aliceToken, "/api/events/" + eventId + "/attend-requests", "{}").andExpect(status().isBadRequest());

		Event past = new Event();
		past.setTitle("Old");
		past.setDescription("Old event");
		past.setEventDate(LocalDate.now(ZoneOffset.UTC).minusDays(14));
		past.setStartTime(LocalTime.of(10, 0));
		past.setEndTime(LocalTime.of(12, 0));
		past.setCreatedAt(Instant.now());
		long pastId = eventRepository.saveAndFlush(past).getId();
		postJson(bobToken, "/api/events/" + pastId + "/attend-requests", "{}").andExpect(status().isBadRequest());

		postJson(bobToken, "/api/events/999999/attend-requests", "{}").andExpect(status().isNotFound());
	}

	// ---- statuses, withdrawal and access ----

	@Test
	void requestCanBeWithdrawnOnlyWhileNew() throws Exception {
		long eventId = approvedEvent(saturday, null);
		long requestId = submitAttend(bobToken, eventId);

		mockMvc.perform(delete("/api/users/me/event-requests/" + requestId).header("Authorization", "Bearer " + aliceToken))
				.andExpect(status().isNotFound());

		long second = submitAttend(bobToken, approvedEvent(sunday, null));
		approve(second).andExpect(status().isOk());
		mockMvc.perform(delete("/api/users/me/event-requests/" + second).header("Authorization", "Bearer " + bobToken))
				.andExpect(status().isConflict());

		mockMvc.perform(delete("/api/users/me/event-requests/" + requestId).header("Authorization", "Bearer " + bobToken))
				.andExpect(status().isNoContent());
		getAs(bobToken, "/api/users/me/event-requests").andExpect(jsonPath("$.length()").value(1));
	}

	private long approvedEventOn(LocalDate date) throws Exception {
		long requestId = submitHost(bobToken, date, null);
		String response = approve(requestId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.eventId")).longValue();
	}

	@Test
	void requestCannotBeReviewedTwice() throws Exception {
		long requestId = submitHost(aliceToken, saturday, null);
		approve(requestId).andExpect(status().isOk());

		approve(requestId).andExpect(status().isConflict());
		postJson(adminToken, "/api/admin/event-requests/" + requestId + "/reject", "{}").andExpect(status().isConflict());
	}

	@Test
	void adminListsNewRequestsFirstAndCanFilter() throws Exception {
		long hostRequest = submitHost(aliceToken, saturday, null);
		long eventId = approvedEventOn(sunday);
		submitAttend(aliceToken, eventId);

		getAs(adminToken, "/api/admin/event-requests")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));
		getAs(adminToken, "/api/admin/event-requests?type=HOST")
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(hostRequest));
		getAs(adminToken, "/api/admin/event-requests?status=APPROVED")
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void usersCannotUseAdminEndpointsAndGuestsCannotApply() throws Exception {
		long requestId = submitHost(aliceToken, saturday, null);

		postJson(aliceToken, "/api/admin/event-requests/" + requestId + "/approve", "").andExpect(status().isForbidden());
		getAs(aliceToken, "/api/admin/event-requests").andExpect(status().isForbidden());
		postJson(null, "/api/events/host-requests", hostBody(sunday, null)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/users/me/event-requests")).andExpect(status().isUnauthorized());
	}

	@Test
	void unverifiedEmailCannotApply() throws Exception {
		saveUser("Dave", "+380500000005", "dave@b.com", Role.USER, false);
		String daveToken = login("dave@b.com");
		long eventId = approvedEvent(saturday, null);

		postJson(daveToken, "/api/events/host-requests", hostBody(sunday, null)).andExpect(status().isForbidden());
		postJson(daveToken, "/api/events/" + eventId + "/attend-requests", "{}").andExpect(status().isForbidden());
	}

	// ---- calendar and admin management ----

	@Test
	void calendarSeparatesUpcomingAndPastEvents() throws Exception {
		approvedEvent(saturday, null);
		LocalDate lastWeek = LocalDate.now(ZoneOffset.UTC).minusDays(7);
		for (int i = 0; i < 2; i++) {
			Event past = new Event();
			past.setTitle("Past " + i);
			past.setDescription("Past event");
			past.setEventDate(lastWeek.minusDays(i * 10L));
			past.setStartTime(LocalTime.of(10, 0));
			past.setEndTime(LocalTime.of(12, 0));
			past.setCreatedAt(Instant.now());
			eventRepository.saveAndFlush(past);
		}

		mockMvc.perform(get("/api/events"))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].title").value("Hackathon"));
		mockMvc.perform(get("/api/events?period=PAST"))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].title").value("Past 0"))
				.andExpect(jsonPath("$[0].past").value(true))
				.andExpect(jsonPath("$[1].title").value("Past 1"));
		mockMvc.perform(get("/api/events?from=" + saturday.plusDays(10))).andExpect(jsonPath("$").isEmpty());
		mockMvc.perform(get("/api/events/999999")).andExpect(status().isNotFound());
	}

	@Test
	void adminCreatesUpdatesAndDeletesEvents() throws Exception {
		String response = postJson(adminToken, "/api/admin/events", """
				{"title":"Meetup","description":"Monthly meetup","date":"%s","startTime":"11:00","endTime":"15:00","capacity":3,"hostUserId":%d}
				""".formatted(sunday, bob.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.hostName").value("Bob"))
				.andReturn().getResponse().getContentAsString();
		long eventId = ((Number) JsonPath.read(response, "$.id")).longValue();

		postJson(adminToken, "/api/admin/events", """
				{"title":"Other","description":"Same day","date":"%s","startTime":"11:00","endTime":"15:00"}
				""".formatted(sunday)).andExpect(status().isConflict());
		postJson(adminToken, "/api/admin/events", """
				{"title":"Ghost","description":"No such host","date":"%s","startTime":"11:00","endTime":"15:00","hostUserId":999999}
				""".formatted(saturday)).andExpect(status().isNotFound());

		mockMvc.perform(patch("/api/admin/events/" + eventId).header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Big Meetup\",\"capacity\":10}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Big Meetup"))
				.andExpect(jsonPath("$.capacity").value(10));

		long requestId = submitAttend(aliceToken, eventId);
		approve(requestId).andExpect(status().isOk());
		mockMvc.perform(patch("/api/admin/events/" + eventId).header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"capacity\":0}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(delete("/api/admin/events/" + eventId).header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/events/" + eventId)).andExpect(status().isNotFound());
		assertThat(requestRepository.findAll()).isEmpty();
	}
}
