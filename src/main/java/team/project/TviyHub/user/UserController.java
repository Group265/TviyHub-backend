package team.project.TviyHub.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

	@PutMapping
	public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
		return userService.updateProfile(Long.valueOf(jwt.getSubject()), request);
	}
}
