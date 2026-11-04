package team.project.TviyHub.gallery;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import team.project.TviyHub.TestcontainersConfig;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
class GalleryFlowTest {

	private static final String PASSWORD = "password123";
	private static final int MAX_SIZE = 5 * 1024 * 1024;

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private GalleryImageRepository galleryRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;

	private String adminToken;
	private String userToken;

	@BeforeEach
	void setUp() throws Exception {
		cleanUp();
		saveUser("Admin", "+380500000001", "admin@b.com", Role.ADMIN);
		saveUser("Alice", "+380500000002", "alice@b.com", Role.USER);
		adminToken = login("admin@b.com");
		userToken = login("alice@b.com");
	}

	@AfterEach
	void cleanUp() {
		galleryRepository.deleteAll();
		userRepository.deleteAll();
	}

	private void saveUser(String name, String phone, String email, Role role) {
		User user = new User();
		user.setName(name);
		user.setPhone(phone);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		user.setRole(role);
		user.setEmailVerified(true);
		userRepository.saveAndFlush(user);
	}

	private String login(String email) throws Exception {
		String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	// Minimal files: just the signature plus some bytes
	private static byte[] jpeg() {
		return bytes(new int[]{0xFF, 0xD8, 0xFF, 0xE0}, 32);
	}

	private static byte[] png() {
		return bytes(new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, 32);
	}

	private static byte[] webp() {
		byte[] data = new byte[32];
		System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, data, 0, 4);
		System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, data, 8, 4);
		return data;
	}

	private static byte[] bytes(int[] signature, int length) {
		byte[] data = new byte[length];
		for (int i = 0; i < signature.length; i++) {
			data[i] = (byte) signature[i];
		}
		return data;
	}

	private ResultActions upload(String token, String filename, String contentType, byte[] data, String caption)
			throws Exception {
		var request = multipart("/api/admin/gallery")
				.file(new MockMultipartFile("file", filename, contentType, data));
		if (caption != null) {
			request.param("caption", caption);
		}
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return mockMvc.perform(request);
	}

	private long uploadOk(String filename, String contentType, byte[] data, String caption) throws Exception {
		String response = upload(adminToken, filename, contentType, data, caption)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	@Test
	void adminUploadsJpegPngAndWebp() throws Exception {
		upload(adminToken, "a.jpg", "image/jpeg", jpeg(), "Hall")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.contentType").value("image/jpeg"))
				.andExpect(jsonPath("$.caption").value("Hall"))
				.andExpect(jsonPath("$.sizeBytes").value(32))
				.andExpect(jsonPath("$.url").isNotEmpty());
		upload(adminToken, "b.png", "image/png", png(), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.contentType").value("image/png"))
				.andExpect(jsonPath("$.caption").doesNotExist());
		upload(adminToken, "c.webp", "image/webp", webp(), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.contentType").value("image/webp"));
	}

	@Test
	void formatIsCheckedByContentNotByNameOrDeclaredType() throws Exception {
		byte[] text = "this is not an image".getBytes(StandardCharsets.UTF_8);
		upload(adminToken, "fake.jpg", "image/jpeg", text, null).andExpect(status().isBadRequest());

		byte[] gif = "GIF89a".getBytes(StandardCharsets.US_ASCII);
		upload(adminToken, "anim.gif", "image/gif", Arrays.copyOf(gif, 32), null).andExpect(status().isBadRequest());

		byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);
		upload(adminToken, "logo.png", "image/png", svg, null).andExpect(status().isBadRequest());

		// a real PNG is accepted even with a wrong name and type
		upload(adminToken, "photo.txt", "text/plain", png(), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.contentType").value("image/png"));
		assertThat(galleryRepository.count()).isEqualTo(1);
	}

	@Test
	void rejectsEmptyMissingAndTooLargeFiles() throws Exception {
		upload(adminToken, "empty.jpg", "image/jpeg", new byte[0], null).andExpect(status().isBadRequest());

		mockMvc.perform(multipart("/api/admin/gallery").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isBadRequest());

		byte[] big = new byte[MAX_SIZE + 1];
		System.arraycopy(jpeg(), 0, big, 0, 4);
		upload(adminToken, "big.jpg", "image/jpeg", big, null).andExpect(status().isPayloadTooLarge());

		byte[] exactlyMax = new byte[MAX_SIZE];
		System.arraycopy(jpeg(), 0, exactlyMax, 0, 4);
		upload(adminToken, "max.jpg", "image/jpeg", exactlyMax, null).andExpect(status().isCreated());

		upload(adminToken, "a.jpg", "image/jpeg", jpeg(), "x".repeat(201)).andExpect(status().isBadRequest());
	}

	@Test
	void listIsPublicNewestFirstAndPaged() throws Exception {
		long first = uploadOk("1.jpg", "image/jpeg", jpeg(), "first");
		long second = uploadOk("2.png", "image/png", png(), "second");
		long third = uploadOk("3.webp", "image/webp", webp(), "third");

		mockMvc.perform(get("/api/gallery"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].id").value(third))
				.andExpect(jsonPath("$.content[1].id").value(second))
				.andExpect(jsonPath("$.content[2].id").value(first))
				.andExpect(jsonPath("$.content[0].caption").value("third"))
				.andExpect(jsonPath("$.content[0].url").value("/api/gallery/" + third + "/image"));

		mockMvc.perform(get("/api/gallery?page=1&size=2"))
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(first))
				.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void imageIsPublicAndReturnsTheSameBytes() throws Exception {
		byte[] data = png();
		long id = uploadOk("a.png", "image/png", data, null);

		mockMvc.perform(get("/api/gallery/" + id + "/image"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png"))
				.andExpect(header().exists("ETag"))
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=86400")))
				.andExpect(content().bytes(data));
		mockMvc.perform(get("/api/gallery/999999/image")).andExpect(status().isNotFound());
	}

	@Test
	void adminDeletesAPhoto() throws Exception {
		long id = uploadOk("a.jpg", "image/jpeg", jpeg(), null);

		mockMvc.perform(delete("/api/admin/gallery/" + id).header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/gallery/" + id + "/image")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/gallery")).andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(delete("/api/admin/gallery/" + id).header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isNotFound());
	}

	@Test
	void onlyAdminsCanUploadAndDelete() throws Exception {
		long id = uploadOk("a.jpg", "image/jpeg", jpeg(), null);

		upload(userToken, "b.jpg", "image/jpeg", jpeg(), null).andExpect(status().isForbidden());
		upload(null, "b.jpg", "image/jpeg", jpeg(), null).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/admin/gallery/" + id).header("Authorization", "Bearer " + userToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/admin/gallery/" + id)).andExpect(status().isUnauthorized());
		assertThat(galleryRepository.count()).isEqualTo(1);
	}
}
