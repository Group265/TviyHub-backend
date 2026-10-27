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
import team.project.TviyHub.auth.dto.LoginRequest;
import team.project.TviyHub.auth.dto.RegisterRequest;
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
}
