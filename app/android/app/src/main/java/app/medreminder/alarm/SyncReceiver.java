package app.medreminder.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The once-a-day sync (a safety net if a push was lost). */
public class SyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                SyncClient.sync(ctx);
            } catch (Exception ignored) {
                // offline: alarms keep working from the stored schedule
            } finally {
                pending.finish();
            }
        }).start();
    }
}
