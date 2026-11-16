package app.medreminder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// We authenticate with our own JWTs, so Spring's default in-memory user is not needed.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling // the missed-dose check (MissedDoseJob)
@EnableAsync      // push messages are sent after the request, so a slow push never slows the app
public class MedicineReminderApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedicineReminderApplication.class, args);
    }
}
