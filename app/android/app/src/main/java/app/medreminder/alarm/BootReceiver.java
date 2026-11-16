package app.medreminder.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * AlarmManager forgets everything on reboot, and times shift when the clock or time zone changes.
 * Re-plan all alarms from the schedule stored on the phone (no internet needed).
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        AlarmService.createChannels(ctx);
        AlarmScheduler.rescheduleAll(ctx); // first, from the stored schedule (works offline)
        SyncClient.scheduleDaily(ctx);
        SyncClient.syncAsync(ctx);          // then fetch any changes made while the phone was off
    }
}
