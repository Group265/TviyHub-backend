package team.project.TviyHub.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.auth.dto.AuthResponse;
import team.project.TviyHub.auth.dto.EmailRequest;
import team.project.TviyHub.auth.dto.LoginRequest;
import team.project.TviyHub.auth.google.GoogleIdTokenVerifier;
import team.project.TviyHub.auth.google.GoogleProfile;
import team.project.TviyHub.auth.dto.ResetPasswordWithCodeRequest;
import team.project.TviyHub.auth.dto.VerifyEmailRequest;
import team.project.TviyHub.exception.BadRequestException;
import team.project.TviyHub.verification.VerificationCodeService;
import team.project.TviyHub.verification.VerificationPurpose;
import team.project.TviyHub.auth.dto.RegisterRequest;
import team.project.TviyHub.auth.refresh.IssuedRefreshToken;
import team.project.TviyHub.auth.refresh.RefreshTokenService;
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
	private final RefreshTokenService refreshTokenService;
	private final GoogleIdTokenVerifier googleIdTokenVerifier;
	// Checked when the email is unknown, so that response time does not reveal whether it exists
	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
					   JwtService jwtService, LoginAttemptService loginAttemptService,
					   VerificationCodeService verificationCodeService, RefreshTokenService refreshTokenService,
					   GoogleIdTokenVerifier googleIdTokenVerifier) {
		this.googleIdTokenVerifier = googleIdTokenVerifier;
		this.refreshTokenService = refreshTokenService;
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
		refreshTokenService.revokeAllForUser(user.getId());
	}

	private String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	@Transactional
	public AuthResponse login(LoginRequest request, String clientAddress) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		String attemptKey = clientAddress + "|" + email;
		loginAttemptService.assertNotLocked(attemptKey);

		Optional<User> user = userRepository.findByEmail(email);
		// Google-only accounts have no password; still run a hash check so the response time is the same
		String hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
		boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
		if (user.isEmpty() || user.get().getPasswordHash() == null || !passwordMatches) {
			loginAttemptService.recordFailure(attemptKey);
			throw new UnauthorizedException("Invalid email or password");
		}

		loginAttemptService.recordSuccess(attemptKey);
		if (user.get().isBlocked()) {
			throw new ForbiddenException("Account is blocked");
		}
		return tokens(user.get(), refreshTokenService.issue(user.get().getId()));
	}

	/**
	 * Signs in with a Google ID token. Finds the user by Google id, otherwise links an existing account
	 * with the same (Google-verified) email, otherwise creates a new account without phone and password.
	 */
	@Transactional
	public AuthResponse loginWithGoogle(String idToken) {
		GoogleProfile profile = googleIdTokenVerifier.verify(idToken);
		if (!profile.emailVerified()) {
			throw new UnauthorizedException("The Google email is not verified");
		}
		String email = normalize(profile.email());

		User user = userRepository.findByGoogleId(profile.subject())
				.or(() -> userRepository.findByEmail(email).map(existing -> {
					existing.setGoogleId(profile.subject());
					if (!existing.isEmailVerified()) {
						// Someone could have registered this email without owning it: drop their password
						existing.setPasswordHash(null);
						existing.setEmailVerified(true);
						refreshTokenService.revokeAllForUser(existing.getId());
					}
					return userRepository.saveAndFlush(existing);
				}))
				.orElseGet(() -> userRepository.saveAndFlush(newGoogleUser(profile, email)));

		if (user.isBlocked()) {
			throw new ForbiddenException("Account is blocked");
		}
		return tokens(user, refreshTokenService.issue(user.getId()));
	}

	private User newGoogleUser(GoogleProfile profile, String email) {
		String name = profile.name() == null || profile.name().isBlank() ? email.substring(0, email.indexOf('@')) : profile.name().trim();
		User user = new User();
		user.setName(name.length() > 100 ? name.substring(0, 100) : name);
		user.setEmail(email);
		user.setGoogleId(profile.subject());
		user.setRole(Role.USER);
		user.setEmailVerified(true);
		return user;
	}

	/** Exchanges a refresh token for a new access token and a new refresh token. */
	@Transactional(noRollbackFor = UnauthorizedException.class)
	public AuthResponse refresh(String refreshToken) {
		IssuedRefreshToken issued = refreshTokenService.rotate(refreshToken);
		return tokens(issued.user(), issued.token());
	}

	public void logout(String refreshToken) {
		refreshTokenService.revoke(refreshToken);
	}

	private AuthResponse tokens(User user, String refreshToken) {
		return new AuthResponse(jwtService.generateToken(user), "Bearer", jwtService.getLifetimeSeconds(),
				refreshToken, refreshTokenService.getLifetimeSeconds());
	}
}
