package app.medreminder.alarm;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

/**
 * Runs when an alarm event is due (FIRE: RING or EXPIRE) and when Taken is tapped on the notification.
 * Always plans the alarm's next event before returning, so the chain never breaks.
 */
public class AlarmReceiver extends BroadcastReceiver {
    static final String ACTION_FIRE = "app.medreminder.alarm.FIRE";
    static final String ACTION_TAKEN = "app.medreminder.alarm.TAKEN";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String id = intent.getStringExtra(AlarmScheduler.EXTRA_ID);
        String key = intent.getStringExtra(AlarmScheduler.EXTRA_KEY);
        long doseAt = intent.getLongExtra(AlarmScheduler.EXTRA_DOSE_AT, 0);
        if (id == null || key == null) {
            return;
        }
        JSONObject alarm = AlarmStore.alarm(ctx, id);
        if (alarm == null) { // removed from the schedule since this was planned
            AlarmScheduler.cancel(ctx, id);
            return;
        }
        if (ACTION_TAKEN.equals(intent.getAction())) {
            taken(ctx, alarm, key, doseAt);
            return;
        }
        String type = intent.getStringExtra(AlarmScheduler.EXTRA_TYPE);
        if (AlarmScheduler.TYPE_RING.equals(type)) {
            if (!AlarmStore.isHandled(ctx, key)) {
                AlarmService.ring(ctx, id, key, doseAt);
            }
        } else if (AlarmScheduler.TYPE_EXPIRE.equals(type)) {
            expired(ctx, alarm, key, doseAt);
        }
        AlarmScheduler.schedule(ctx, alarm, System.currentTimeMillis());
    }

    /** "వేసుకున్నాను" tapped (on the alarm screen or the notification). */
    static void taken(Context ctx, JSONObject alarm, String key, long doseAt) {
        finish(ctx, alarm, key, doseAt, AlarmStore.STATUS_TAKEN);
    }

    /** The automatic stop time passed with no Taken: the dose is missed. */
    static void expired(Context ctx, JSONObject alarm, String key, long doseAt) {
        finish(ctx, alarm, key, doseAt, AlarmStore.STATUS_MISSED);
    }

    private static void finish(Context ctx, JSONObject alarm, String key, long doseAt, String status) {
        String id = alarm.optString("id");
        if (AlarmStore.markHandled(ctx, key, status) && !alarm.optBoolean("test")) {
            String date = AlarmScheduler.dateOf(key, doseAt, AlarmScheduler.zone(ctx));
            JSONObject log = AlarmStore.addLog(ctx, id, date, status);
            MedAlarmPlugin.emitDoseLogged(log);
        }
        SyncClient.uploadAsync(ctx, null); // tell the family now if online; otherwise it stays queued
        AlarmService.stopIfRinging(ctx, key);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(AlarmService.notificationId(key));
        AlarmScheduler.schedule(ctx, alarm, System.currentTimeMillis());
    }
}
