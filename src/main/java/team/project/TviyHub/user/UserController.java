package team.project.TviyHub.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.project.TviyHub.user.dto.ChangeEmailRequest;
import team.project.TviyHub.user.dto.ChangePasswordRequest;
import team.project.TviyHub.user.dto.UpdateUserDto;
import team.project.TviyHub.user.dto.UserProfileResponse;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping
	public UserProfileResponse me(@AuthenticationPrincipal User user) {
		return userService.getProfile(user.getId());
	}

	@PatchMapping
	public UserProfileResponse update(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateUserDto request) {
		return userService.updateProfile(user.getId(), request);
	}

	@PatchMapping("/email")
	public UserProfileResponse changeEmail(@AuthenticationPrincipal User user, @Valid @RequestBody ChangeEmailRequest request) {
		return userService.changeEmail(user.getId(), request);
	}

	@PatchMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@AuthenticationPrincipal User user, @Valid @RequestBody ChangePasswordRequest request) {
		userService.changePassword(user.getId(), request);
	}
}
