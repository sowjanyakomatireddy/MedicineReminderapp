package app.medreminder.push;

import java.util.Map;

/** Used when FIREBASE_CREDENTIALS is not set: nothing is sent. Phones still sync on start and daily. */
public class NoopPushSender implements PushSender {
    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public Result toPhone(String token, Map<String, String> data) {
        return Result.DISABLED;
    }

    @Override
    public Result toFamily(String token, String title, String body, String link, Map<String, String> data) {
        return Result.DISABLED;
    }
}
