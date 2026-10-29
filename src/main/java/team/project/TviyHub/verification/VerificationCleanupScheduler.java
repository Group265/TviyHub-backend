package team.project.TviyHub.verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationCleanupScheduler {

	private final VerificationCodeRepository repository;
	private final Clock clock;

	@Scheduled(cron = "0 30 * * * *")
	@Transactional
	public void deleteExpiredCodes() {
		int deleted = repository.deleteExpired(clock.instant());
		if (deleted > 0) {
			log.info("Removed {} expired verification codes", deleted);
		}
	}
}
