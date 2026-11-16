package app.medreminder.push;

import app.medreminder.config.AppProperties;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class PushConfig {
    @Bean
    PushSender pushSender(AppProperties props) throws IOException {
        String file = props.push().credentialsFile();
        if (file == null || file.isBlank()) {
            LoggerFactory.getLogger(PushConfig.class)
                    .info("Push messages are off (FIREBASE_CREDENTIALS not set). Phones sync on start and daily.");
            return new NoopPushSender();
        }
        return new FcmPushSender(file);
    }
}
