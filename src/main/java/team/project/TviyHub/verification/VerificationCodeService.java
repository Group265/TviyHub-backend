package team.project.TviyHub.verification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.exception.BadRequestException;
import team.project.TviyHub.exception.TooManyRequestsException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class VerificationCodeService {

	private static final String INVALID_CODE = "Invalid or expired code";

	private final SecureRandom random = new SecureRandom();

	private final VerificationCodeRepository repository;
	private final VerificationAttemptRecorder attemptRecorder;
	private final PasswordEncoder passwordEncoder;
	private final EmailService emailService;
	private final Clock clock;
	private final Duration codeTtl;
	private final int maxAttempts;
	private final Duration resendCooldown;

	public VerificationCodeService(VerificationCodeRepository repository,
								   VerificationAttemptRecorder attemptRecorder,
								   PasswordEncoder passwordEncoder,
								   EmailService emailService,
								   Clock clock,
								   @Value("${app.verification.code-ttl-minutes}") long codeTtlMinutes,
								   @Value("${app.verification.max-attempts}") int maxAttempts,
								   @Value("${app.verification.resend-cooldown-seconds}") long resendCooldownSeconds) {
		this.repository = repository;
		this.attemptRecorder = attemptRecorder;
		this.passwordEncoder = passwordEncoder;
		this.emailService = emailService;
		this.clock = clock;
		this.codeTtl = Duration.ofMinutes(codeTtlMinutes);
		this.maxAttempts = maxAttempts;
		this.resendCooldown = Duration.ofSeconds(resendCooldownSeconds);
	}

	/**
	 * Creates (or replaces) a code for the user and purpose and emails it to {@code recipientEmail}.
	 *
	 * @param payload data to apply once the code is confirmed; may be null
	 */
	@Transactional
	public void issue(Long userId, VerificationPurpose purpose, String payload, String recipientEmail) {
		Instant now = clock.instant();
		Optional<VerificationCode> existing = repository.findByUserIdAndPurpose(userId, purpose);
		if (existing.isPresent() && existing.get().getCreatedAt().plus(resendCooldown).isAfter(now)) {
			throw new TooManyRequestsException("Please wait before requesting a new code");
		}

		String code = "%06d".formatted(random.nextInt(1_000_000));
		// Update the existing row in place: a delete followed by an insert would hit the unique key
		VerificationCode entity = existing.orElseGet(VerificationCode::new);
		entity.setUserId(userId);
		entity.setPurpose(purpose);
		entity.setCodeHash(passwordEncoder.encode(code));
		entity.setPayload(payload);
		entity.setAttempts(0);
		entity.setCreatedAt(now);
		entity.setExpiresAt(now.plus(codeTtl));
		repository.saveAndFlush(entity);

		emailService.send(recipientEmail, subject(purpose), body(code));
	}

	/**
	 * Checks the code and, when it is correct, removes it and returns its payload (may be null).
	 * A wrong code counts as an attempt; after the allowed number of attempts it is invalidated.
	 */
	@Transactional
	public String consume(Long userId, VerificationPurpose purpose, String code) {
		VerificationCode entity = repository.findByUserIdAndPurpose(userId, purpose)
				.orElseThrow(() -> new BadRequestException(INVALID_CODE));
		if (entity.getExpiresAt().isBefore(clock.instant())) {
			throw new BadRequestException(INVALID_CODE);
		}
		if (!passwordEncoder.matches(code, entity.getCodeHash())) {
			attemptRecorder.recordFailure(entity.getId(), maxAttempts);
			throw new BadRequestException(INVALID_CODE);
		}
		String payload = entity.getPayload();
		repository.delete(entity);
		return payload;
	}

	private String subject(VerificationPurpose purpose) {
		return switch (purpose) {
			case EMAIL_VERIFICATION -> "TviyHub: confirm your email";
			case PASSWORD_CHANGE -> "TviyHub: confirm your password change";
			case PHONE_CHANGE -> "TviyHub: confirm your phone number change";
			case EMAIL_CHANGE -> "TviyHub: confirm your new email";
			case PASSWORD_RESET -> "TviyHub: reset your password";
		};
	}

	private String body(String code) {
		return "Your TviyHub confirmation code: " + code + "\n"
				+ "It is valid for " + codeTtl.toMinutes() + " minutes.\n"
				+ "If you did not request this, ignore this email and do not share the code with anyone.";
	}
}
