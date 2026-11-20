package team.project.TviyHub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Locale;

@SpringBootApplication
@EnableScheduling
public class TviyHubApplication {

	public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
		SpringApplication.run(TviyHubApplication.class, args);
	}

}
