package team.project.TviyHub.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

	Optional<VerificationCode> findByUserIdAndPurpose(Long userId, VerificationPurpose purpose);

	@Modifying
	@Query("delete from VerificationCode c where c.expiresAt < :now")
	int deleteExpired(@Param("now") Instant now);
}
