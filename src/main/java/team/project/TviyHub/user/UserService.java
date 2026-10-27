package team.project.TviyHub.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.exception.BadRequestException;
import team.project.TviyHub.exception.ConflictException;
import team.project.TviyHub.exception.UnauthorizedException;
import team.project.TviyHub.user.dto.ChangeEmailRequest;
import team.project.TviyHub.user.dto.ChangePasswordRequest;
import team.project.TviyHub.user.dto.UpdateUserDto;
import team.project.TviyHub.user.dto.UserProfileResponse;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional(readOnly = true)
	public UserProfileResponse getProfile(Long userId) {
		return UserProfileResponse.from(find(userId));
	}

	@Transactional
	public UserProfileResponse updateProfile(Long userId, UpdateUserDto request) {
		User user = find(userId);
		if (request.phone() != null) {
			String phone = request.phone().trim();
			if (userRepository.existsByPhoneAndIdNot(phone, userId)) {
				throw new ConflictException("Phone is already registered");
			}
			user.setPhone(phone);
		}
		if (request.name() != null) {
			user.setName(request.name().trim());
		}
		return UserProfileResponse.from(userRepository.saveAndFlush(user));
	}

	@Transactional
	public UserProfileResponse changeEmail(Long userId, ChangeEmailRequest request) {
		User user = find(userId);
		requireCurrentPassword(user, request.currentPassword());
		String email = request.newEmail().trim().toLowerCase(Locale.ROOT);
		if (userRepository.existsByEmailAndIdNot(email, userId)) {
			throw new ConflictException("Email is already registered");
		}
		user.setEmail(email);
		return UserProfileResponse.from(userRepository.saveAndFlush(user));
	}

	@Transactional
	public void changePassword(Long userId, ChangePasswordRequest request) {
		User user = find(userId);
		requireCurrentPassword(user, request.currentPassword());
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepository.saveAndFlush(user);
	}

	private void requireCurrentPassword(User user, String currentPassword) {
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new BadRequestException("Current password is incorrect");
		}
	}

	private User find(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new UnauthorizedException("User no longer exists"));
	}
}
