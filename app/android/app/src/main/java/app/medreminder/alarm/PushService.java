package app.medreminder.alarm;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Data pushes from the server, handled even when the app is closed:
 *  SYNC — fetch the new schedule now (a medicine was added or changed);
 *  RING — the family pressed "Play the reminder again": ring that medicine now.
 */
public class PushService extends FirebaseMessagingService {
    @Override
    public void onNewToken(@NonNull String token) {
        SyncClient.uploadToken(this, token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        String type = message.getData().get("type");
        if ("SYNC".equals(type)) {
            try {
                SyncClient.sync(this); // runs on Firebase's background thread; about 10 s are allowed
            } catch (Exception e) {
                Log.i(AlarmScheduler.TAG, "Push sync failed; will retry later");
            }
        } else if ("RING".equals(type)) {
            ringNow(this, message.getData().get("scheduleId"), message.getData().get("date"));
        }
    }

    /** Rings one medicine immediately. Taken then counts for that dose's date (replacing MISSED). */
    static void ringNow(Context ctx, String scheduleId, String date) {
        JSONObject a = scheduleId == null ? null : AlarmStore.alarm(ctx, scheduleId);
        if (a == null || date == null) {
            return;
        }
        String key = scheduleId + "@" + date;
        if (AlarmStore.isTaken(ctx, key)) {
            return;
        }
        AlarmService.ring(ctx, scheduleId, key, doseAt(a, date, AlarmScheduler.zone(ctx)));
    }

    private static long doseAt(JSONObject a, String date, TimeZone tz) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
            f.setTimeZone(tz);
            return f.parse(date + " " + a.optString("time", "00:00")).getTime();
        } catch (Exception e) {
            return Calendar.getInstance().getTimeInMillis();
        }
    }
}
