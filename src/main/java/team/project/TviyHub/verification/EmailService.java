package team.project.TviyHub.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text emails over SMTP. When MAIL_HOST is not configured the message is only written
 * to the application log, which is meant for local development and must not be used in production.
 */
@Slf4j
@Service
public class EmailService {

	private final ObjectProvider<JavaMailSender> mailSender;
	private final String host;
	private final String from;

	public EmailService(ObjectProvider<JavaMailSender> mailSender,
						@Value("${spring.mail.host:}") String host,
						@Value("${app.mail.from}") String from) {
		this.mailSender = mailSender;
		this.host = host;
		this.from = from;
	}

	/** Tells at startup which mode is active, so it is clear why codes do or do not arrive. No secrets are logged. */
	@jakarta.annotation.PostConstruct
	void logMode() {
		if (host.isBlank()) {
			log.warn("Email: MAIL_HOST is empty, codes are only written to this log (see .env.team / README)");
		} else {
			log.info("Email: SMTP {}, from={}", host, from);
		}
	}

	public void send(String to, String subject, String text) {
		JavaMailSender sender = host.isBlank() ? null : mailSender.getIfAvailable();
		if (sender == null) {
			log.info("Email delivery is not configured (MAIL_HOST is empty). To: {} | Subject: {} | {}",
					to, subject, text);
			return;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(text);
		sender.send(message);
	}
}
