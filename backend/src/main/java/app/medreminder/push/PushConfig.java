package app.medreminder.push;

import app.medreminder.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PushConfig {
    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    /**
     * FIREBASE_CREDENTIALS is either the path to the service-account JSON file, or the JSON itself (handy on hosts
     * where a pasted variable is easier than a file). If it cannot be used, push is turned off and the server
     * still starts: phones then sync on start and daily, as without Firebase.
     */
    @Bean
    PushSender pushSender(AppProperties props) {
        String value = props.push().credentialsFile();
        if (value == null || value.isBlank()) {
            log.info("Push messages are off (FIREBASE_CREDENTIALS not set). Phones sync on start and daily.");
            return new NoopPushSender();
        }
        try {
            return new FcmPushSender(value.trim());
        } catch (Exception e) {
            // Never log the value: it may be the private key itself.
            log.error("Push messages are off: FIREBASE_CREDENTIALS could not be used ({}). Check the file or pasted key.",
                    e.getClass().getSimpleName());
            return new NoopPushSender();
        }
    }
}
