package app.medreminder.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import app.medreminder.MainActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Works out when each alarm rings next and registers it with AlarmManager.setAlarmClock, which is exact,
 * wakes the phone from Doze and is not throttled (so a 5-minute repeat really comes after 5 minutes).
 *
 * One dose ("occurrence") of an alarm: first ring at (time - remindBefore), then every repeatEvery minutes,
 * until (time + stopAfter), when it is marked MISSED if nobody tapped Taken.
 * Each alarm id has at most one pending AlarmManager entry: its next RING or its EXPIRE.
 */
public final class AlarmScheduler {
    static final String TAG = "MedAlarm";
    static final String TYPE_RING = "RING";
    static final String TYPE_EXPIRE = "EXPIRE";
    static final String EXTRA_ID = "alarmId";
    static final String EXTRA_KEY = "occurrence";
    static final String EXTRA_TYPE = "type";
    static final String EXTRA_DOSE_AT = "doseAt";

    private static final long MINUTE = 60_000L;

    private AlarmScheduler() {
    }

    /** A planned event for one alarm. */
    static final class Event {
        final String type;
        final long at;
        final String occurrenceKey;
        final long doseAt;

        Event(String type, long at, String occurrenceKey, long doseAt) {
            this.type = type;
            this.at = at;
            this.occurrenceKey = occurrenceKey;
            this.doseAt = doseAt;
        }
    }

    /** One dose of an alarm on one day. */
    static final class Occurrence {
        final String key;
        final String date;
        final long doseAt;
        final long start;
        final long end;

        Occurrence(String key, String date, long doseAt, long start, long end) {
            this.key = key;
            this.date = date;
            this.doseAt = doseAt;
            this.start = start;
            this.end = end;
        }
    }

    // ---------------------------------------------------------------- planning (pure logic)

    static TimeZone zone(Context ctx) {
        String tz = AlarmStore.config(ctx).optString("timeZone", "Asia/Kolkata");
        return TimeZone.getTimeZone(tz);
    }

    /** Doses of this alarm from yesterday (a window can cross midnight) to a week ahead, in time order. */
    static List<Occurrence> occurrences(JSONObject a, TimeZone tz, long now) {
        List<Occurrence> list = new ArrayList<>();
        long before = a.optInt("remindBeforeMin", 5) * MINUTE;
        long after = a.optInt("stopAfterMin", 30) * MINUTE;
        String id = a.optString("id");
        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        dateFmt.setTimeZone(tz);

        if (a.has("at")) { // one-off (test) alarm: "at" is the first ring
            long start = a.optLong("at");
            long doseAt = start + before;
            list.add(new Occurrence(id + "@" + start, dateFmt.format(doseAt), doseAt, start, doseAt + after));
            return list;
        }
        String[] hm = a.optString("time", "00:00").split(":");
        int hour = Integer.parseInt(hm[0]);
        int minute = Integer.parseInt(hm[1]);
        if (a.has("date")) { // one-off on a calendar date, e.g. the period reminder
            String[] ymd = a.optString("date").split("-");
            Calendar c = Calendar.getInstance(tz);
            c.clear();
            c.set(Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]) - 1, Integer.parseInt(ymd[2]), hour, minute, 0);
            long doseAt = c.getTimeInMillis();
            String date = dateFmt.format(doseAt);
            list.add(new Occurrence(id + "@" + date, date, doseAt, doseAt - before, doseAt + after));
            return list;
        }
        Set<Integer> days = new HashSet<>();
        JSONArray d = a.optJSONArray("days");
        for (int i = 0; d != null && i < d.length(); i++) {
            days.add(d.optInt(i));
        }
        for (int offset = -1; offset <= 8; offset++) {
            Calendar c = Calendar.getInstance(tz);
            c.setTimeInMillis(now);
            c.add(Calendar.DAY_OF_MONTH, offset);
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            int dow = c.get(Calendar.DAY_OF_WEEK); // Sunday = 1 ... Saturday = 7
            int iso = dow == Calendar.SUNDAY ? 7 : dow - 1; // Monday = 1 ... Sunday = 7
            if (!days.contains(iso)) {
                continue;
            }
            long doseAt = c.getTimeInMillis();
            String date = dateFmt.format(doseAt);
            list.add(new Occurrence(id + "@" + date, date, doseAt, doseAt - before, doseAt + after));
        }
        return list;
    }

    /**
     * The next event strictly after {@code after}, skipping doses already handled.
     * Package-private and side-effect free apart from reading the store, so it is easy to reason about.
     */
    static Event next(Context ctx, JSONObject a, long after) {
        long repeat = Math.max(1, a.optInt("repeatEveryMin", 5)) * MINUTE;
        for (Occurrence o : occurrences(a, zone(ctx), after)) {
            if (o.end <= after || AlarmStore.isHandled(ctx, o.key)) {
                continue;
            }
            if (o.start > after) {
                return new Event(TYPE_RING, o.start, o.key, o.doseAt);
            }
            long k = (after - o.start) / repeat + 1;
            long ring = o.start + k * repeat;
            return ring < o.end
                    ? new Event(TYPE_RING, ring, o.key, o.doseAt)
                    : new Event(TYPE_EXPIRE, o.end, o.key, o.doseAt);
        }
        return null;
    }

    // ---------------------------------------------------------------- AlarmManager

    /** Re-plans every alarm. Called when the schedule changes, after boot, update, or a clock change. */
    public static synchronized void rescheduleAll(Context ctx) {
        long now = System.currentTimeMillis();
        markLateMisses(ctx, now);
        Set<String> wanted = new HashSet<>();
        for (JSONObject a : AlarmStore.alarms(ctx)) {
            String id = a.optString("id");
            wanted.add(id);
            schedule(ctx, a, now);
        }
        for (String old : AlarmStore.scheduledIds(ctx)) {
            if (!wanted.contains(old)) {
                cancel(ctx, old);
            }
        }
        AlarmStore.setScheduledIds(ctx, wanted);
    }

    /** Plans the next event of one alarm after {@code after}. */
    static void schedule(Context ctx, JSONObject a, long after) {
        String id = a.optString("id");
        Event e;
        try {
            e = next(ctx, a, after);
        } catch (RuntimeException ex) {
            Log.w(TAG, "Skipping an alarm with a bad time");
            return;
        }
        if (e == null) {
            cancel(ctx, id);
            return;
        }
        PendingIntent pi = fireIntent(ctx, id, e);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        try {
            if (canScheduleExact(ctx)) {
                PendingIntent show = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(e.at, show), pi);
            } else {
                // Without the exact-alarm permission the alarm may be late; the setup screen asks for it.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi);
            }
        } catch (SecurityException ex) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi);
        }
    }

    /**
     * If the phone was off or the app was stopped when a dose window ended, it was never marked.
     * Count those as missed, but only for alarms that already existed when that dose started.
     */
    private static void markLateMisses(Context ctx, long now) {
        for (JSONObject a : AlarmStore.alarms(ctx)) {
            String id = a.optString("id");
            long since = AlarmStore.firstSeen(ctx, id);
            for (Occurrence o : occurrences(a, zone(ctx), now)) {
                if (o.end <= now && o.start >= since && now - o.end < 24 * 60 * MINUTE
                        && AlarmStore.markHandled(ctx, o.key, AlarmStore.STATUS_MISSED) && !a.optBoolean("test")) {
                    AlarmStore.addLog(ctx, id, o.date, AlarmStore.STATUS_MISSED);
                }
            }
        }
    }

    static boolean canScheduleExact(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return am.canScheduleExactAlarms();
    }

    static void cancel(Context ctx, String id) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(fireIntent(ctx, id, null));
    }

    /** One PendingIntent per alarm id (the data URI makes them distinct); extras say what to do. */
    private static PendingIntent fireIntent(Context ctx, String id, Event e) {
        Intent i = new Intent(ctx, AlarmReceiver.class)
                .setAction(AlarmReceiver.ACTION_FIRE)
                .setData(Uri.parse("medalarm://alarm/" + Uri.encode(id)));
        if (e != null) {
            i.putExtra(EXTRA_ID, id)
                    .putExtra(EXTRA_KEY, e.occurrenceKey)
                    .putExtra(EXTRA_TYPE, e.type)
                    .putExtra(EXTRA_DOSE_AT, e.doseAt);
        }
        return PendingIntent.getBroadcast(ctx, id.hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** For the debug screen: each alarm's next event. */
    static JSONArray describe(Context ctx) {
        JSONArray out = new JSONArray();
        long now = System.currentTimeMillis();
        for (JSONObject a : AlarmStore.alarms(ctx)) {
            Event e = next(ctx, a, now);
            try {
                out.put(new JSONObject()
                        .put("id", a.optString("id"))
                        .put("title", a.optString("title"))
                        .put("next", e == null ? JSONObject.NULL : e.at)
                        .put("type", e == null ? JSONObject.NULL : e.type)
                        .put("test", a.optBoolean("test")));
            } catch (org.json.JSONException ignored) {
                // skip
            }
        }
        return out;
    }

    /** The calendar date ("yyyy-MM-dd") of a dose, from its occurrence key. */
    static String dateOf(String occurrenceKey, long doseAt, TimeZone tz) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        f.setTimeZone(tz);
        return f.format(doseAt);
    }
}
