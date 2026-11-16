package team.project.TviyHub.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Creates the first administrator at startup so that the admin panel can be opened on a fresh database.
 * Does nothing when an administrator already exists, so it never overwrites or recreates anything.
 * The default credentials are for development and testing only: set ADMIN_EMAIL and ADMIN_PASSWORD
 * (or ADMIN_BOOTSTRAP_ENABLED=false) before running anywhere real.
 */
@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

	static final String DEFAULT_PASSWORD = "12345678";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final boolean enabled;
	private final String email;
	private final String password;
	private final String name;

	public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
						  @Value("${app.bootstrap-admin.enabled:true}") boolean enabled,
						  @Value("${app.bootstrap-admin.email}") String email,
						  @Value("${app.bootstrap-admin.password}") String password,
						  @Value("${app.bootstrap-admin.name:Administrator}") String name) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.enabled = enabled;
		this.email = email.trim().toLowerCase(Locale.ROOT);
		this.password = password;
		this.name = name;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (enabled) {
			ensureAdmin();
		}
	}

	/** Creates the administrator, or promotes the user with the configured email, if there is no administrator yet. */
	@Transactional
	public void ensureAdmin() {
		if (userRepository.countByRole(Role.ADMIN) > 0) {
			return;
		}
		Optional<User> existing = userRepository.findByEmail(email);
		if (existing.isPresent()) {
			User user = existing.get();
			user.setRole(Role.ADMIN);
			user.setEmailVerified(true);
			userRepository.saveAndFlush(user);
			log.warn("Administrator: the existing user {} was made an ADMIN because there was none", email);
			return;
		}
		User admin = new User();
		admin.setName(name);
		admin.setEmail(email);
		admin.setPasswordHash(passwordEncoder.encode(password));
		admin.setRole(Role.ADMIN);
		admin.setEmailVerified(true);
		userRepository.saveAndFlush(admin);
		if (DEFAULT_PASSWORD.equals(password)) {
			log.warn("Administrator {} created with the DEFAULT password. Set ADMIN_PASSWORD (or change the "
					+ "password) before using this anywhere real.", email);
		} else {
			log.info("Administrator created: {}", email);
		}
	}
}
