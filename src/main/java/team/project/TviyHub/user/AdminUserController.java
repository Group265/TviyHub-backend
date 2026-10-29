package team.project.TviyHub.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.project.TviyHub.config.OpenApiConfig;
import team.project.TviyHub.user.dto.AdminCreateUserRequest;
import team.project.TviyHub.user.dto.AdminUpdateUserRequest;
import team.project.TviyHub.user.dto.AdminUserResponse;
import team.project.TviyHub.user.dto.ChangeRoleRequest;
import team.project.TviyHub.user.dto.PageResponse;
import team.project.TviyHub.user.dto.ResetPasswordRequest;
import team.project.TviyHub.user.dto.UserStatsResponse;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin: users", description = "User management (ADMIN role only)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponse(responseCode = "401", description = "Authentication required")
@ApiResponse(responseCode = "403", description = "ADMIN role required")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

	private final AdminUserService adminUserService;

	@GetMapping
	@Operation(summary = "List users",
			description = "Paged list. Filters: search (name, email or phone), role, blocked. "
					+ "sortBy is one of id, name, email, createdAt; size is capped at 100.")
	@ApiResponse(responseCode = "400", description = "Invalid sort field")
	public PageResponse<AdminUserResponse> list(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Role role,
			@RequestParam(required = false) Boolean blocked,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt") String sortBy,
			@RequestParam(defaultValue = "desc") String direction) {
		return adminUserService.list(search, role, blocked, page, size, sortBy, "desc".equalsIgnoreCase(direction));
	}

	@GetMapping("/stats")
	@Operation(summary = "User counters", description = "Total users, administrators and blocked users.")
	public UserStatsResponse stats() {
		return adminUserService.stats();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a user by id")
	@ApiResponse(responseCode = "404", description = "User not found")
	public AdminUserResponse get(@PathVariable Long id) {
		return adminUserService.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a user with a chosen role", description = "Use this to create administrators.")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@ApiResponse(responseCode = "409", description = "Email or phone is already registered")
	public AdminUserResponse create(@Valid @RequestBody AdminCreateUserRequest request) {
		return adminUserService.create(request);
	}

	@PatchMapping("/{id}")
	@Operation(summary = "Update name, phone and/or email", description = "Only the provided fields are changed.")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@ApiResponse(responseCode = "404", description = "User not found")
	@ApiResponse(responseCode = "409", description = "Email or phone is already registered")
	public AdminUserResponse update(@PathVariable Long id, @Valid @RequestBody AdminUpdateUserRequest request) {
		return adminUserService.update(id, request);
	}

	@PatchMapping("/{id}/role")
	@Operation(summary = "Change a user's role",
			description = "Takes effect immediately. An administrator cannot change their own role.")
	@ApiResponse(responseCode = "400", description = "Validation failed or own role")
	@ApiResponse(responseCode = "404", description = "User not found")
	public AdminUserResponse changeRole(@AuthenticationPrincipal User admin, @PathVariable Long id,
										@Valid @RequestBody ChangeRoleRequest request) {
		return adminUserService.changeRole(admin.getId(), id, request.role());
	}

	@PostMapping("/{id}/block")
	@Operation(summary = "Block a user",
			description = "The user cannot log in and existing tokens stop working immediately. "
					+ "An administrator cannot block themselves.")
	@ApiResponse(responseCode = "400", description = "Cannot block yourself")
	@ApiResponse(responseCode = "404", description = "User not found")
	public AdminUserResponse block(@AuthenticationPrincipal User admin, @PathVariable Long id) {
		return adminUserService.setBlocked(admin.getId(), id, true);
	}

	@PostMapping("/{id}/unblock")
	@Operation(summary = "Unblock a user")
	@ApiResponse(responseCode = "404", description = "User not found")
	public AdminUserResponse unblock(@AuthenticationPrincipal User admin, @PathVariable Long id) {
		return adminUserService.setBlocked(admin.getId(), id, false);
	}

	@PostMapping("/{id}/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Set a new password for a user",
			description = "Tokens issued before the reset stay valid until they expire.")
	@ApiResponse(responseCode = "204", description = "Password changed")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@ApiResponse(responseCode = "404", description = "User not found")
	public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
		adminUserService.resetPassword(id, request.newPassword());
	}
}
