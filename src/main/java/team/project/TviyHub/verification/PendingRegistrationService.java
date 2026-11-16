package team.project.TviyHub.verification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.exception.BadRequestException;
import team.project.TviyHub.exception.TooManyRequestsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Holds registrations until their email code is confirmed; only then does the caller create the account.
 * The email address of a pending registration is not reserved: registering again replaces it.
 */
@Service
public class PendingRegistrationService {

	private static final String INVALID_CODE = "Invalid or expired code";

	private final PendingRegistrationRepository repository;
	private final PendingAttemptRecorder attemptRecorder;
	private final VerificationCodeService codes;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final int maxAttempts;
	private final Duration resendCooldown;

	public PendingRegistrationService(PendingRegistrationRepository repository, PendingAttemptRecorder attemptRecorder,
									  VerificationCodeService codes, PasswordEncoder passwordEncoder, Clock clock,
									  @Value("${app.verification.max-attempts}") int maxAttempts,
									  @Value("${app.verification.resend-cooldown-seconds}") long resendCooldownSeconds) {
		this.repository = repository;
		this.attemptRecorder = attemptRecorder;
		this.codes = codes;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.maxAttempts = maxAttempts;
		this.resendCooldown = Duration.ofSeconds(resendCooldownSeconds);
	}

	public long codeTtlSeconds() {
		return codes.getCodeTtl().toSeconds();
	}

	/** Stores (or replaces) the pending registration and emails the code. */
	@Transactional
	public void start(String name, String phone, String email, String passwordHash) {
		Optional<PendingRegistration> existing = repository.findByEmail(email);
		throttle(existing);

		PendingRegistration pending = existing.orElseGet(PendingRegistration::new);
		pending.setEmail(email);
		pending.setName(name);
		pending.setPhone(phone);
		pending.setPasswordHash(passwordHash);
		issueCode(pending);
	}

	public boolean exists(String email) {
		return repository.findByEmail(email).isPresent();
	}

	/** Checks the code and, when it is right, removes the pending registration and returns its data. */
	@Transactional
	public PendingRegistration confirm(String email, String code) {
		PendingRegistration pending = repository.findByEmail(email)
				.orElseThrow(() -> new BadRequestException(INVALID_CODE));
		if (pending.getExpiresAt().isBefore(clock.instant())) {
			throw new BadRequestException(INVALID_CODE);
		}
		if (!passwordEncoder.matches(code, pending.getCodeHash())) {
			attemptRecorder.recordFailure(pending.getId(), maxAttempts);
			throw new BadRequestException(INVALID_CODE);
		}
		repository.delete(pending);
		return pending;
	}

	private void throttle(Optional<PendingRegistration> existing) {
		if (existing.isPresent() && existing.get().getCreatedAt().plus(resendCooldown).isAfter(clock.instant())) {
			throw new TooManyRequestsException("Please wait before requesting a new code");
		}
	}

	private void issueCode(PendingRegistration pending) {
		Instant now = clock.instant();
		String code = codes.generateCode();
		pending.setCodeHash(passwordEncoder.encode(code));
		pending.setAttempts(0);
		pending.setCreatedAt(now);
		pending.setExpiresAt(now.plus(codes.getCodeTtl()));
		repository.saveAndFlush(pending);
		codes.sendRegistrationCode(pending.getEmail(), code);
	}
}
