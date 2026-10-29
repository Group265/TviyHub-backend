package team.project.TviyHub.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.exception.BadRequestException;
import team.project.TviyHub.exception.ConflictException;
import team.project.TviyHub.exception.ForbiddenException;
import team.project.TviyHub.exception.UnauthorizedException;
import team.project.TviyHub.user.dto.ChangeEmailRequest;
import team.project.TviyHub.user.dto.ChangePasswordRequest;
import team.project.TviyHub.user.dto.ChangePhoneRequest;
import team.project.TviyHub.user.dto.UpdateUserDto;
import team.project.TviyHub.user.dto.UserProfileResponse;
import team.project.TviyHub.verification.VerificationCodeService;
import team.project.TviyHub.verification.VerificationPurpose;

import java.util.Locale;

/**
 * Own-profile operations. Password, phone and email changes are two-step: a request sends a code
 * by email and stores the pending value, a confirm call with the code applies it.
 */
@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final VerificationCodeService verificationCodeService;

	@Transactional(readOnly = true)
	public UserProfileResponse getProfile(Long userId) {
		return UserProfileResponse.from(find(userId));
	}

	@Transactional
	public UserProfileResponse updateProfile(Long userId, UpdateUserDto request) {
		User user = find(userId);
		if (request.name() != null) {
			user.setName(request.name().trim());
		}
		return UserProfileResponse.from(userRepository.saveAndFlush(user));
	}

	@Transactional
	public void requestPasswordChange(Long userId, ChangePasswordRequest request) {
		User user = findVerified(userId);
		requireCurrentPassword(user, request.currentPassword());
		verificationCodeService.issue(userId, VerificationPurpose.PASSWORD_CHANGE,
				passwordEncoder.encode(request.newPassword()), user.getEmail());
	}

	@Transactional
	public void confirmPasswordChange(Long userId, String code) {
		User user = findVerified(userId);
		user.setPasswordHash(verificationCodeService.consume(userId, VerificationPurpose.PASSWORD_CHANGE, code));
		userRepository.saveAndFlush(user);
	}

	@Transactional
	public void requestPhoneChange(Long userId, ChangePhoneRequest request) {
		User user = findVerified(userId);
		String phone = request.newPhone().trim();
		if (userRepository.existsByPhoneAndIdNot(phone, userId)) {
			throw new ConflictException("Phone is already registered");
		}
		verificationCodeService.issue(userId, VerificationPurpose.PHONE_CHANGE, phone, user.getEmail());
	}

	@Transactional
	public UserProfileResponse confirmPhoneChange(Long userId, String code) {
		User user = findVerified(userId);
		String phone = verificationCodeService.consume(userId, VerificationPurpose.PHONE_CHANGE, code);
		if (userRepository.existsByPhoneAndIdNot(phone, userId)) {
			throw new ConflictException("Phone is already registered");
		}
		user.setPhone(phone);
		return UserProfileResponse.from(userRepository.saveAndFlush(user));
	}

	@Transactional
	public void requestEmailChange(Long userId, ChangeEmailRequest request) {
		User user = findVerified(userId);
		requireCurrentPassword(user, request.currentPassword());
		String email = request.newEmail().trim().toLowerCase(Locale.ROOT);
		if (userRepository.existsByEmailAndIdNot(email, userId)) {
			throw new ConflictException("Email is already registered");
		}
		// The code goes to the NEW address, which proves the user owns it
		verificationCodeService.issue(userId, VerificationPurpose.EMAIL_CHANGE, email, email);
	}

	@Transactional
	public UserProfileResponse confirmEmailChange(Long userId, String code) {
		User user = findVerified(userId);
		String email = verificationCodeService.consume(userId, VerificationPurpose.EMAIL_CHANGE, code);
		if (userRepository.existsByEmailAndIdNot(email, userId)) {
			throw new ConflictException("Email is already registered");
		}
		user.setEmail(email);
		return UserProfileResponse.from(userRepository.saveAndFlush(user));
	}

	private void requireCurrentPassword(User user, String currentPassword) {
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new BadRequestException("Current password is incorrect");
		}
	}

	private User findVerified(Long userId) {
		User user = find(userId);
		if (!user.isEmailVerified()) {
			throw new ForbiddenException("Verify your email first");
		}
		return user;
	}

	private User find(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new UnauthorizedException("User no longer exists"));
	}
}
