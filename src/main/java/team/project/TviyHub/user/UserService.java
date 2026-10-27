package team.project.TviyHub.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.user.dto.UpdateProfileRequest;
import team.project.TviyHub.user.dto.UserResponse;
import team.project.TviyHub.common.exception.ConflictException;
import team.project.TviyHub.common.exception.UnauthorizedException;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public UserResponse getProfile(Long userId) {
		return UserResponse.from(find(userId));
	}

	@Transactional
	public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
		User user = find(userId);
		String phone = request.phone().trim();
		if (userRepository.existsByPhoneAndIdNot(phone, userId)) {
			throw new ConflictException("Phone is already registered");
		}
		user.setName(request.name().trim());
		user.setPhone(phone);
		return UserResponse.from(userRepository.saveAndFlush(user));
	}

	private User find(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new UnauthorizedException("User no longer exists"));
	}
}
