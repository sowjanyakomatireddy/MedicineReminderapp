package app.medreminder.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushFcmOptions;
import com.google.firebase.messaging.WebpushNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Firebase Cloud Messaging through the Admin SDK. Credentials: FIREBASE_CREDENTIALS, the service-account JSON
 * file's path or its contents (never committed). Message contents are never logged (they name medicines).
 */
public class FcmPushSender implements PushSender {
    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private final FirebaseMessaging messaging;

    public FcmPushSender(String credentials) throws IOException {
        try (InputStream in = credentials.startsWith("{")
                ? new ByteArrayInputStream(credentials.getBytes(StandardCharsets.UTF_8))
                : new FileInputStream(credentials)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            this.messaging = FirebaseMessaging.getInstance(app);
        }
        log.info("Push messages enabled (Firebase Cloud Messaging)");
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Result toPhone(String token, Map<String, String> data) {
        Message m = Message.builder()
                .setToken(token)
                .putAllData(data)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH) // wakes the phone from Doze
                        .setTtl(60 * 60 * 1000L)
                        .build())
                .build();
        return send(m);
    }

    @Override
    public Result toFamily(String token, String title, String body, String link, Map<String, String> data) {
        Message m = Message.builder()
                .setToken(token)
                .putAllData(data)
                .setWebpushConfig(WebpushConfig.builder()
                        .putHeader("Urgency", "high")
                        .setNotification(WebpushNotification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .setIcon("/icon-192.png")
                                .setRequireInteraction(true)
                                .build())
                        .setFcmOptions(WebpushFcmOptions.withLink(link))
                        .build())
                .build();
        return send(m);
    }

    private Result send(Message m) {
        try {
            messaging.send(m);
            return Result.SENT;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                return Result.INVALID_TOKEN;
            }
            log.warn("Push failed: {}", code);
            return Result.FAILED;
        }
    }
}
