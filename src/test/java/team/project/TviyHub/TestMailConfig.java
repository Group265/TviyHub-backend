package team.project.TviyHub;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import team.project.TviyHub.verification.EmailService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Replaces the email sender so tests can read the codes that would have been emailed. */
@TestConfiguration(proxyBeanMethods = false)
public class TestMailConfig {

	@Bean
	@Primary
	CapturingEmailService capturingEmailService(ObjectProvider<JavaMailSender> sender) {
		return new CapturingEmailService(sender);
	}

	public static class CapturingEmailService extends EmailService {

		private static final Pattern CODE = Pattern.compile("code: ([0-9]{6})");

		private final Map<String, String> lastCodes = new ConcurrentHashMap<>();
		private final Map<String, Integer> counts = new ConcurrentHashMap<>();

		CapturingEmailService(ObjectProvider<JavaMailSender> sender) {
			super(sender, "", "test@tviyhub.local");
		}

		@Override
		public void send(String to, String subject, String text) {
			Matcher m = CODE.matcher(text);
			if (m.find()) {
				lastCodes.put(to, m.group(1));
			}
			counts.merge(to, 1, Integer::sum);
		}

		public String lastCode(String email) {
			return lastCodes.get(email);
		}

		public int sentCount(String email) {
			return counts.getOrDefault(email, 0);
		}

		public void reset() {
			lastCodes.clear();
			counts.clear();
		}
	}
}
