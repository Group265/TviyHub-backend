package team.project.TviyHub.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.auth.dto.LoginRequest;
import team.project.TviyHub.auth.dto.RegisterRequest;
import team.project.TviyHub.auth.dto.TokenResponse;
import team.project.TviyHub.user.dto.UserResponse;
import team.project.TviyHub.common.exception.ConflictException;
import team.project.TviyHub.common.exception.UnauthorizedException;
import team.project.TviyHub.user.Role;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	@Transactional
	public UserResponse register(RegisterRequest request) {
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

		return UserResponse.from(userRepository.saveAndFlush(user));
	}

	@Transactional(readOnly = true)
	public TokenResponse login(LoginRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		User user = userRepository.findByEmail(email)
				.filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
				.orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
		return jwtService.issueToken(user);
	}
}
