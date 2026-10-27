package team.project.TviyHub.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.auth.dto.AuthResponse;
import team.project.TviyHub.auth.dto.LoginRequest;
import team.project.TviyHub.auth.dto.RegisterRequest;
import team.project.TviyHub.exception.ConflictException;
import team.project.TviyHub.exception.ForbiddenException;
import team.project.TviyHub.exception.UnauthorizedException;
import team.project.TviyHub.security.JwtService;
import team.project.TviyHub.security.LoginAttemptService;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;
import team.project.TviyHub.user.dto.UserProfileResponse;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final LoginAttemptService loginAttemptService;
	// Checked when the email is unknown, so that response time does not reveal whether it exists
	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
					   JwtService jwtService, LoginAttemptService loginAttemptService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.loginAttemptService = loginAttemptService;
		this.dummyPasswordHash = passwordEncoder.encode("timing-equalizer");
	}

	@Transactional
	public UserProfileResponse register(RegisterRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		String phone = request.phone().trim();

		if (userRepository.existsByEmail(email)) {
			throw new ConflictException("Email is already registered");
		}
		if (userRepository.existsByPhone(phone)) {
			throw new ConflictException("Phone is already registered");
		}

		User user = new User();
		user.setName(request.name().trim());
		user.setPhone(phone);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setRole(Role.USER);

		return UserProfileResponse.from(userRepository.saveAndFlush(user));
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request, String clientAddress) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		String attemptKey = clientAddress + "|" + email;
		loginAttemptService.assertNotLocked(attemptKey);

		Optional<User> user = userRepository.findByEmail(email);
		String hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
		boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
		if (user.isEmpty() || !passwordMatches) {
			loginAttemptService.recordFailure(attemptKey);
			throw new UnauthorizedException("Invalid email or password");
		}

		loginAttemptService.recordSuccess(attemptKey);
		if (user.get().isBlocked()) {
			throw new ForbiddenException("Account is blocked");
		}
		return new AuthResponse(jwtService.generateToken(user.get()), "Bearer", jwtService.getLifetimeSeconds());
	}
}
