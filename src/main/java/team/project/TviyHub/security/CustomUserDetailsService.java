package team.project.TviyHub.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import team.project.TviyHub.user.UserRepository;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}

	public UserDetails loadUserById(Long id) throws UsernameNotFoundException {
		return userRepository.findById(id)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}
}
