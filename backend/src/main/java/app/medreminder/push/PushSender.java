package app.medreminder.push;

import java.util.Map;

/** Sends push messages. FcmPushSender when Firebase is configured, otherwise NoopPushSender. */
public interface PushSender {
    enum Result { SENT, INVALID_TOKEN, FAILED, DISABLED }

    boolean enabled();

    /**
     * A data-only, high-priority message to the elder's Android phone. The app handles it even when closed
     * (SYNC: fetch the schedule; RING: ring a reminder now).
     */
    Result toPhone(String token, Map<String, String> data);

    /** A visible alert to a family member's browser or phone; tapping it opens {@code link}. */
    Result toFamily(String token, String title, String body, String link, Map<String, String> data);
}
