package team.project.TviyHub.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records a wrong code attempt in its own transaction, so the counter survives the caller's rollback. */
@Component
@RequiredArgsConstructor
public class PendingAttemptRecorder {

	private final PendingRegistrationRepository repository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(Long id, int maxAttempts) {
		repository.findById(id).ifPresent(pending -> {
			if (pending.getAttempts() + 1 >= maxAttempts) {
				repository.delete(pending);
			} else {
				pending.setAttempts(pending.getAttempts() + 1);
			}
		});
	}
}
