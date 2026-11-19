package team.project.TviyHub.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import team.project.TviyHub.TestMailConfig;
import team.project.TviyHub.TestMailConfig.CapturingEmailService;
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.user.UserRepository;
import team.project.TviyHub.verification.PendingRegistrationRepository;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfig.class, TestMailConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class NameValidationTest {

	private static final List<String> VALID = List.of("Olena Shevchenko", "Олена Шевченко", "Иван Петров",
			"Ґжеґж Їжак Єва", "Анна-Марія Коваль", "Дар'я Іванова", "Jan O'Neil", "Ёлкин Ёж", "Mix Мікс");
	private static final List<String> INVALID = List.of("Olena1", "Олена_Шевченко", "Olena!", "Ольга 2", "Anna  Maria",
			"-Anna", "Anna-", "'Anna", "Zoë Müller", "Ольга.", "Łukasz Żak");

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PendingRegistrationRepository pendingRepository;
	@Autowired
	private CapturingEmailService mail;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		pendingRepository.deleteAll();
		mail.reset();
	}

	private ResultActions register(String name, String email) throws Exception {
		String json = "{\"name\":%s,\"phone\":\"+380671234567\",\"email\":\"%s\",\"password\":\"password123\",\"confirmPassword\":\"password123\"}"
				.formatted(quote(name), email);
		return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String quote(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\n") + "\"";
	}

	@Test
	void registrationAcceptsEnglishUkrainianAndRussianNames() throws Exception {
		for (String name : VALID) {
			pendingRepository.deleteAll();
			register(name, "a@b.com").andExpect(status().isAccepted());
		}
	}

	@Test
	void registrationRejectsDigitsSymbolsAndOtherAlphabets() throws Exception {
		for (String name : INVALID) {
			register(name, "a@b.com").andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.name").isNotEmpty());
		}
		org.assertj.core.api.Assertions.assertThat(pendingRepository.count()).isZero();
	}

	@Test
	void profileUpdateUsesTheSameRule() throws Exception {
		register("Olena Test", "a@b.com").andExpect(status().isAccepted());
		String response = mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"a@b.com\",\"code\":\"%s\"}".formatted(mail.lastCode("a@b.com"))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(response, "$.accessToken");

		for (String name : INVALID) {
			patchName(token, name).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").isNotEmpty());
		}
		for (String name : VALID) {
			patchName(token, name).andExpect(status().isOk()).andExpect(jsonPath("$.name").value(name));
		}
	}

	private ResultActions patchName(String token, String name) throws Exception {
		return mockMvc.perform(patch("/api/users/me").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":" + quote(name) + "}"));
	}
}
