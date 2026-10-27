package team.project.TviyHub.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

	boolean existsByEmail(String email);

	Optional<User> findByEmail(String email);

	boolean existsByEmailAndIdNot(String email, Long id);

	boolean existsByPhoneAndIdNot(String phone, Long id);

	boolean existsByPhone(String phone);

	long countByRole(Role role);

	long countByBlocked(boolean blocked);
}
