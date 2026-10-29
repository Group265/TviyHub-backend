package team.project.TviyHub.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.auth.dto.AuthResponse;
import team.project.TviyHub.auth.dto.EmailRequest;
import team.project.TviyHub.auth.dto.LoginRequest;
import team.project.TviyHub.auth.dto.ResetPasswordWithCodeRequest;
import team.project.TviyHub.auth.dto.VerifyEmailRequest;
import team.project.TviyHub.exception.BadRequestException;
import team.project.TviyHub.verification.VerificationCodeService;
import team.project.TviyHub.verification.VerificationPurpose;
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
	private final VerificationCodeService verificationCodeService;
	// Checked when the email is unknown, so that response time does not reveal whether it exists
	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
					   JwtService jwtService, LoginAttemptService loginAttemptService,
					   VerificationCodeService verificationCodeService) {
		this.verificationCodeService = verificationCodeService;
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
		user.setEmailVerified(false);

		User saved = userRepository.saveAndFlush(user);
		verificationCodeService.issue(saved.getId(), VerificationPurpose.EMAIL_VERIFICATION, null, saved.getEmail());
		return UserProfileResponse.from(saved);
	}

	@Transactional
	public void verifyEmail(VerifyEmailRequest request) {
		User user = userRepository.findByEmail(normalize(request.email()))
				.orElseThrow(() -> new BadRequestException("Invalid or expired code"));
		verificationCodeService.consume(user.getId(), VerificationPurpose.EMAIL_VERIFICATION, request.code());
		user.setEmailVerified(true);
		userRepository.saveAndFlush(user);
	}

	/** Always completes silently, so the response does not reveal whether the email is registered. */
	@Transactional
	public void resendVerification(EmailRequest request) {
		Optional<User> user = userRepository.findByEmail(normalize(request.email()));
		if (user.isPresent() && !user.get().isEmailVerified() && !user.get().isBlocked()) {
			verificationCodeService.issue(user.get().getId(), VerificationPurpose.EMAIL_VERIFICATION, null, user.get().getEmail());
		}
	}

	/** Always completes silently, so the response does not reveal whether the email is registered. */
	@Transactional
	public void forgotPassword(EmailRequest request) {
		Optional<User> user = userRepository.findByEmail(normalize(request.email()));
		if (user.isPresent() && !user.get().isBlocked()) {
			verificationCodeService.issue(user.get().getId(), VerificationPurpose.PASSWORD_RESET, null, user.get().getEmail());
		}
	}

	@Transactional
	public void resetPassword(ResetPasswordWithCodeRequest request) {
		User user = userRepository.findByEmail(normalize(request.email()))
				.filter(u -> !u.isBlocked())
				.orElseThrow(() -> new BadRequestException("Invalid or expired code"));
		verificationCodeService.consume(user.getId(), VerificationPurpose.PASSWORD_RESET, request.code());
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepository.saveAndFlush(user);
	}

	private String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
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
