package team.project.TviyHub.user;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.exception.BadRequestException;
import team.project.TviyHub.exception.ConflictException;
import team.project.TviyHub.exception.ResourceNotFoundException;
import team.project.TviyHub.user.dto.AdminCreateUserRequest;
import team.project.TviyHub.user.dto.AdminUpdateUserRequest;
import team.project.TviyHub.user.dto.AdminUserResponse;
import team.project.TviyHub.user.dto.PageResponse;
import team.project.TviyHub.user.dto.UserStatsResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminUserService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "email", "createdAt");

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional(readOnly = true)
	public PageResponse<AdminUserResponse> list(String search, Role role, Boolean blocked,
												int page, int size, String sortBy, boolean descending) {
		if (!SORTABLE_FIELDS.contains(sortBy)) {
			throw new BadRequestException("sortBy must be one of " + SORTABLE_FIELDS);
		}
		Sort sort = descending ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
		PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
		Page<AdminUserResponse> result = userRepository.findAll(filter(search, role, blocked), pageRequest)
				.map(AdminUserResponse::from);
		return PageResponse.from(result);
	}

	@Transactional(readOnly = true)
	public AdminUserResponse get(Long id) {
		return AdminUserResponse.from(find(id));
	}

	@Transactional(readOnly = true)
	public UserStatsResponse stats() {
		return new UserStatsResponse(userRepository.count(),
				userRepository.countByRole(Role.ADMIN), userRepository.countByBlocked(true));
	}

	@Transactional
	public AdminUserResponse create(AdminCreateUserRequest request) {
		String email = normalizeEmail(request.email());
		String phone = request.phone().trim();
		if (userRepository.existsByEmail(email)) {
			throw new ConflictException("Email is already registered");
		}
		if (userRepository.existsByPhone(phone)) {
			throw new ConflictException("Phone is already registered");
		}
		User user = new User();
		user.setName(request.name().trim());
		user.setPhone(phone);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setRole(request.role());
		return AdminUserResponse.from(userRepository.saveAndFlush(user));
	}

	@Transactional
	public AdminUserResponse update(Long id, AdminUpdateUserRequest request) {
		User user = find(id);
		if (request.email() != null) {
			String email = normalizeEmail(request.email());
			if (userRepository.existsByEmailAndIdNot(email, id)) {
				throw new ConflictException("Email is already registered");
			}
			user.setEmail(email);
		}
		if (request.phone() != null) {
			String phone = request.phone().trim();
			if (userRepository.existsByPhoneAndIdNot(phone, id)) {
				throw new ConflictException("Phone is already registered");
			}
			user.setPhone(phone);
		}
		if (request.name() != null) {
			user.setName(request.name().trim());
		}
		return AdminUserResponse.from(userRepository.saveAndFlush(user));
	}

	@Transactional
	public AdminUserResponse changeRole(Long adminId, Long id, Role role) {
		requireNotSelf(adminId, id, "You cannot change your own role");
		User user = find(id);
		user.setRole(role);
		return AdminUserResponse.from(userRepository.saveAndFlush(user));
	}

	@Transactional
	public AdminUserResponse setBlocked(Long adminId, Long id, boolean blocked) {
		if (blocked) {
			requireNotSelf(adminId, id, "You cannot block yourself");
		}
		User user = find(id);
		user.setBlocked(blocked);
		return AdminUserResponse.from(userRepository.saveAndFlush(user));
	}

	@Transactional
	public void resetPassword(Long id, String newPassword) {
		User user = find(id);
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		userRepository.saveAndFlush(user);
	}

	private Specification<User> filter(String search, Role role, Boolean blocked) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (role != null) {
				predicates.add(cb.equal(root.get("role"), role));
			}
			if (blocked != null) {
				predicates.add(cb.equal(root.get("blocked"), blocked));
			}
			if (search != null && !search.isBlank()) {
				String like = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
				predicates.add(cb.or(
						cb.like(cb.lower(root.get("name")), like, '\\'),
						cb.like(cb.lower(root.get("email")), like, '\\'),
						cb.like(root.get("phone"), like, '\\')));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static void requireNotSelf(Long adminId, Long targetId, String message) {
		if (adminId.equals(targetId)) {
			throw new BadRequestException(message);
		}
	}

	private User find(Long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
	}
}
