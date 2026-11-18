package app.medreminder.push;

import app.medreminder.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushConfigTest {

    private static PushSender senderFor(String credentials) {
        AppProperties props = new AppProperties(null, null, null,
                new AppProperties.Push(credentials, "https://app.example"), null);
        return new PushConfig().pushSender(props);
    }

    @Test
    void offWhenNotSet() {
        assertThat(senderFor("")).isInstanceOf(NoopPushSender.class);
    }

    @Test
    void aMissingFileTurnsPushOffInsteadOfStoppingTheServer() {
        assertThat(senderFor("/etc/secrets/does-not-exist.json")).isInstanceOf(NoopPushSender.class);
    }

    @Test
    void aBrokenPastedKeyTurnsPushOffInsteadOfStoppingTheServer() {
        assertThat(senderFor("{\"type\": \"service_account\"}")).isInstanceOf(NoopPushSender.class);
    }
}
