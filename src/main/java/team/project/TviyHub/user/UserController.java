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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.project.TviyHub.config.OpenApiConfig;
import team.project.TviyHub.user.dto.ChangeEmailRequest;
import team.project.TviyHub.user.dto.ChangePasswordRequest;
import team.project.TviyHub.user.dto.ChangePhoneRequest;
import team.project.TviyHub.user.dto.CodeConfirmationRequest;
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
	@Operation(summary = "Update name", description = "Phone, email and password are changed through the code-confirmed endpoints.")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	public UserProfileResponse update(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateUserDto request) {
		return userService.updateProfile(user.getId(), request);
	}

	@PostMapping("/password/request")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Change password: send code", description = "Requires the current password and a verified email. A code is sent to the current email.")
	@ApiResponse(responseCode = "204", description = "Code sent")
	@ApiResponse(responseCode = "400", description = "Validation failed or current password is incorrect")
	@ApiResponse(responseCode = "403", description = "Email is not verified")
	@ApiResponse(responseCode = "429", description = "Code was requested too recently")
	public void requestPasswordChange(@AuthenticationPrincipal User user, @Valid @RequestBody ChangePasswordRequest request) {
		userService.requestPasswordChange(user.getId(), request);
	}

	@PostMapping("/password/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Change password: confirm with code", description = "Previously issued tokens stay valid until they expire.")
	@ApiResponse(responseCode = "204", description = "Password changed")
	@ApiResponse(responseCode = "400", description = "Invalid or expired code")
	public void confirmPasswordChange(@AuthenticationPrincipal User user, @Valid @RequestBody CodeConfirmationRequest request) {
		userService.confirmPasswordChange(user.getId(), request.code());
	}

	@PostMapping("/phone/request")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Change phone: send code", description = "Requires a verified email. A code is sent to the current email.")
	@ApiResponse(responseCode = "204", description = "Code sent")
	@ApiResponse(responseCode = "403", description = "Email is not verified")
	@ApiResponse(responseCode = "409", description = "Phone is already in use")
	@ApiResponse(responseCode = "429", description = "Code was requested too recently")
	public void requestPhoneChange(@AuthenticationPrincipal User user, @Valid @RequestBody ChangePhoneRequest request) {
		userService.requestPhoneChange(user.getId(), request);
	}

	@PostMapping("/phone/confirm")
	@Operation(summary = "Change phone: confirm with code")
	@ApiResponse(responseCode = "400", description = "Invalid or expired code")
	@ApiResponse(responseCode = "409", description = "Phone is already in use")
	public UserProfileResponse confirmPhoneChange(@AuthenticationPrincipal User user, @Valid @RequestBody CodeConfirmationRequest request) {
		return userService.confirmPhoneChange(user.getId(), request.code());
	}

	@PostMapping("/email/request")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Change email: send code", description = "Requires the current password and a verified email. A code is sent to the NEW address.")
	@ApiResponse(responseCode = "204", description = "Code sent")
	@ApiResponse(responseCode = "400", description = "Validation failed or current password is incorrect")
	@ApiResponse(responseCode = "403", description = "Email is not verified")
	@ApiResponse(responseCode = "409", description = "Email is already in use")
	@ApiResponse(responseCode = "429", description = "Code was requested too recently")
	public void requestEmailChange(@AuthenticationPrincipal User user, @Valid @RequestBody ChangeEmailRequest request) {
		userService.requestEmailChange(user.getId(), request);
	}

	@PostMapping("/email/confirm")
	@Operation(summary = "Change email: confirm with code (sent to the new address)")
	@ApiResponse(responseCode = "400", description = "Invalid or expired code")
	@ApiResponse(responseCode = "409", description = "Email is already in use")
	public UserProfileResponse confirmEmailChange(@AuthenticationPrincipal User user, @Valid @RequestBody CodeConfirmationRequest request) {
		return userService.confirmEmailChange(user.getId(), request.code());
	}
}
