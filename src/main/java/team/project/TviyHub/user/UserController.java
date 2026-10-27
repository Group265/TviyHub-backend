package team.project.TviyHub.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import team.project.TviyHub.config.OpenApiConfig;
import team.project.TviyHub.user.dto.ChangeEmailRequest;
import team.project.TviyHub.user.dto.ChangePasswordRequest;
import team.project.TviyHub.user.dto.UpdateUserDto;
import team.project.TviyHub.user.dto.UserProfileResponse;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Profile of the current user")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponse(responseCode = "401", description = "Authentication required")
public class UserController {

	private final UserService userService;

	@GetMapping
	@Operation(summary = "Get own profile")
	public UserProfileResponse me(@AuthenticationPrincipal User user) {
		return userService.getProfile(user.getId());
	}

	@PatchMapping
	@Operation(summary = "Update name and/or phone", description = "Only the provided fields are changed. The role cannot be changed.")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@ApiResponse(responseCode = "409", description = "Phone is already in use")
	public UserProfileResponse update(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateUserDto request) {
		return userService.updateProfile(user.getId(), request);
	}

	@PatchMapping("/email")
	@Operation(summary = "Change email", description = "Requires the current password.")
	@ApiResponse(responseCode = "400", description = "Validation failed or current password is incorrect")
	@ApiResponse(responseCode = "409", description = "Email is already in use")
	public UserProfileResponse changeEmail(@AuthenticationPrincipal User user, @Valid @RequestBody ChangeEmailRequest request) {
		return userService.changeEmail(user.getId(), request);
	}

	@PatchMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Change password", description = "Requires the current password. Previously issued tokens stay valid until they expire.")
	@ApiResponse(responseCode = "204", description = "Password changed")
	@ApiResponse(responseCode = "400", description = "Validation failed or current password is incorrect")
	public void changePassword(@AuthenticationPrincipal User user, @Valid @RequestBody ChangePasswordRequest request) {
		userService.changePassword(user.getId(), request);
	}
}
