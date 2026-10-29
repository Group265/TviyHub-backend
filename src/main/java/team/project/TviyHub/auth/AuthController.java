package team.project.TviyHub.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.project.TviyHub.auth.dto.AuthResponse;
import team.project.TviyHub.auth.dto.EmailRequest;
import team.project.TviyHub.auth.dto.LoginRequest;
import team.project.TviyHub.auth.dto.ResetPasswordWithCodeRequest;
import team.project.TviyHub.auth.dto.VerifyEmailRequest;
import team.project.TviyHub.auth.dto.RegisterRequest;
import team.project.TviyHub.auth.dto.RefreshTokenRequest;
import team.project.TviyHub.user.dto.UserProfileResponse;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration and login (public endpoints)")
public class AuthController {

	private final AuthService authService;

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Register a new user", description = "The role is always USER. The phone must be in international format.")
	@ApiResponse(responseCode = "201", description = "User created")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@ApiResponse(responseCode = "409", description = "Email or phone is already registered")
	@ApiResponse(responseCode = "429", description = "Too many requests")
	public UserProfileResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	@Operation(summary = "Log in", description = "Returns a JWT for the Authorization: Bearer header.")
	@ApiResponse(responseCode = "200", description = "Token issued")
	@ApiResponse(responseCode = "401", description = "Invalid email or password")
	@ApiResponse(responseCode = "429", description = "Too many requests or login temporarily locked")
	public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		return authService.login(request, httpRequest.getRemoteAddr());
	}

	@PostMapping("/refresh")
	@Operation(summary = "Get a new access token", description = "Exchanges a refresh token for a new access token and a new refresh token. The old refresh token stops working; reusing it revokes the whole session.")
	@ApiResponse(responseCode = "200", description = "Tokens issued")
	@ApiResponse(responseCode = "401", description = "Invalid, expired or already used refresh token")
	@ApiResponse(responseCode = "403", description = "Account is blocked")
	@ApiResponse(responseCode = "429", description = "Too many requests")
	public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return authService.refresh(request.refreshToken());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Log out", description = "Revokes the session of the given refresh token. Always returns 204, even for an unknown token.")
	@ApiResponse(responseCode = "204", description = "Logged out")
	public void logout(@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(request.refreshToken());
	}

	@PostMapping("/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Confirm the email with the code sent at registration")
	@ApiResponse(responseCode = "204", description = "Email verified")
	@ApiResponse(responseCode = "400", description = "Invalid or expired code")
	public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		authService.verifyEmail(request);
	}

	@PostMapping("/resend-verification")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Send a new verification code", description = "Always returns 204, whether or not the email is registered.")
	@ApiResponse(responseCode = "204", description = "Accepted")
	@ApiResponse(responseCode = "429", description = "Code was requested too recently")
	public void resendVerification(@Valid @RequestBody EmailRequest request) {
		authService.resendVerification(request);
	}

	@PostMapping("/forgot-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Send a password reset code", description = "Always returns 204, whether or not the email is registered.")
	@ApiResponse(responseCode = "204", description = "Accepted")
	@ApiResponse(responseCode = "429", description = "Code was requested too recently")
	public void forgotPassword(@Valid @RequestBody EmailRequest request) {
		authService.forgotPassword(request);
	}

	@PostMapping("/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Set a new password using the emailed code")
	@ApiResponse(responseCode = "204", description = "Password changed")
	@ApiResponse(responseCode = "400", description = "Invalid or expired code, or validation failed")
	public void resetPassword(@Valid @RequestBody ResetPasswordWithCodeRequest request) {
		authService.resetPassword(request);
	}
}
