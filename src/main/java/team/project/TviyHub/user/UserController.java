package team.project.TviyHub.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.project.TviyHub.user.dto.ChangeEmailRequest;
import team.project.TviyHub.user.dto.ChangePasswordRequest;
import team.project.TviyHub.user.dto.UpdateProfileRequest;
import team.project.TviyHub.user.dto.UserResponse;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping
	public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		return userService.getProfile(Long.valueOf(jwt.getSubject()));
	}

	@PatchMapping
	public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
		return userService.updateProfile(Long.valueOf(jwt.getSubject()), request);
	}

	@PatchMapping("/email")
	public UserResponse changeEmail(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangeEmailRequest request) {
		return userService.changeEmail(Long.valueOf(jwt.getSubject()), request);
	}

	@PatchMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
		userService.changePassword(Long.valueOf(jwt.getSubject()), request);
	}
}
