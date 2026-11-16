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
import team.project.TviyHub.auth.dto.GoogleLoginRequest;
import team.project.TviyHub.auth.dto.LoginRequest;
import team.project.TviyHub.auth.dto.RegistrationPendingResponse;
import team.project.TviyHub.auth.dto.ResetPasswordWithCodeRequest;
import team.project.TviyHub.auth.dto.VerifyEmailRequest;
import team.project.TviyHub.auth.dto.RegisterRequest;
import team.project.TviyHub.auth.dto.RefreshTokenRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration and login (public endpoints)")
public class AuthController {

	private final AuthService authService;

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "Start a registration",
			description = "The account is NOT created yet: a 6-digit code is emailed and the data waits for it. "
					+ "Confirm with POST /api/auth/verify-email, which creates the account and signs the user in. "
					+ "The role is always USER; the phone must be in international format. "
					+ "Registering again with the same email replaces the earlier pending registration.")
	@ApiResponse(responseCode = "202", description = "The code was emailed")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@ApiResponse(responseCode = "409", description = "Email or phone is already registered")
	@ApiResponse(responseCode = "429", description = "Code was requested too recently, or too many requests")
	@ApiResponse(responseCode = "503", description = "The email could not be sent")
	public RegistrationPendingResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	@Operation(summary = "Log in", description = "Returns a JWT for the Authorization: Bearer header.")
	@ApiResponse(responseCode = "200", description = "Token issued")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@ApiResponse(responseCode = "401", description = "Invalid email or password (also for accounts created through Google that have no password yet)")
	@ApiResponse(responseCode = "403", description = "Account is blocked")
	@ApiResponse(responseCode = "429", description = "Too many requests or login temporarily locked")
	public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		return authService.login(request, httpRequest.getRemoteAddr());
	}

	@PostMapping("/google")
	@Operation(summary = "Log in with Google", description = "Takes the ID token from Google Sign-In on the frontend. Creates the account on the first sign-in or links an existing account with the same email. Returns the same tokens as login.")
	@ApiResponse(responseCode = "200", description = "Tokens issued")
	@ApiResponse(responseCode = "400", description = "idToken is missing")
	@ApiResponse(responseCode = "401", description = "Invalid Google token or the Google email is not verified")
	@ApiResponse(responseCode = "403", description = "Account is blocked")
	@ApiResponse(responseCode = "429", description = "Too many requests")
	@ApiResponse(responseCode = "503", description = "Google sign-in is not configured")
	public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
		return authService.loginWithGoogle(request.idToken());
	}

	@PostMapping("/refresh")
	@Operation(summary = "Get a new access token", description = "Exchanges a refresh token for a new access token and a new refresh token. The old refresh token stops working; reusing it revokes the whole session.")
	@ApiResponse(responseCode = "200", description = "Tokens issued")
	@ApiResponse(responseCode = "400", description = "refreshToken is missing")
	@ApiResponse(responseCode = "401", description = "Invalid, expired or already used refresh token")
	@ApiResponse(responseCode = "403", description = "Account is blocked")
	@ApiResponse(responseCode = "429", description = "Too many requests")
	public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return authService.refresh(request.refreshToken());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Log out", description = "Revokes the session of the given refresh token and immediately invalidates all access tokens of that user. Always returns 204, even for an unknown token.")
	@ApiResponse(responseCode = "204", description = "Logged out")
	@ApiResponse(responseCode = "400", description = "refreshToken is missing")
	@ApiResponse(responseCode = "429", description = "Too many requests")
	public void logout(@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(request.refreshToken());
	}

	@PostMapping("/verify-email")
	@Operation(summary = "Confirm the registration with the emailed code",
			description = "Creates the account and returns the same tokens as login, so the user is signed in right away. "
					+ "An unknown email gets the same 400 as a wrong code, so the response does not reveal whether the email is registered. "
					+ "After 5 wrong codes the pending registration is deleted: register again.")
	@ApiResponse(responseCode = "200", description = "Account created (or verified), tokens issued")
	@ApiResponse(responseCode = "400", description = "Invalid or expired code, or validation failed")
	@ApiResponse(responseCode = "403", description = "Account is blocked")
	@ApiResponse(responseCode = "409", description = "The email or phone was taken while the code was pending")
	@ApiResponse(responseCode = "429", description = "Too many requests")
	public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		return authService.verifyEmail(request);
	}

	@PostMapping("/forgot-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Send a password reset code", description = "Always returns 204, whether or not the email is registered.")
	@ApiResponse(responseCode = "204", description = "Accepted")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@ApiResponse(responseCode = "429", description = "Code was requested too recently, or too many requests")
	public void forgotPassword(@Valid @RequestBody EmailRequest request) {
		authService.forgotPassword(request);
	}

	@PostMapping("/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Set a new password using the emailed code",
			description = "Ends all sessions of the user. Also the way for a Google account to set its first password.")
	@ApiResponse(responseCode = "204", description = "Password changed")
	@ApiResponse(responseCode = "400", description = "Invalid or expired code, or validation failed")
	@ApiResponse(responseCode = "429", description = "Too many requests")
	public void resetPassword(@Valid @RequestBody ResetPasswordWithCodeRequest request) {
		authService.resetPassword(request);
	}
}
