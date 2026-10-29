package team.project.TviyHub.auth.refresh;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

	private final RefreshTokenRepository repository;
	private final Clock clock;

	@Scheduled(cron = "0 45 * * * *")
	@Transactional
	public void deleteExpiredTokens() {
		int deleted = repository.deleteExpired(clock.instant());
		if (deleted > 0) {
			log.info("Removed {} expired refresh tokens", deleted);
		}
	}
}
