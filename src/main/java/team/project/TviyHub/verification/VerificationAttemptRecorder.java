package team.project.TviyHub.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a wrong code attempt in its own transaction, so the counter survives the exception
 * that rolls back the caller's transaction.
 */
@Component
@RequiredArgsConstructor
public class VerificationAttemptRecorder {

	private final VerificationCodeRepository repository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(Long codeId, int maxAttempts) {
		repository.findById(codeId).ifPresent(code -> {
			if (code.getAttempts() + 1 >= maxAttempts) {
				repository.delete(code);
			} else {
				code.setAttempts(code.getAttempts() + 1);
			}
		});
	}
}
