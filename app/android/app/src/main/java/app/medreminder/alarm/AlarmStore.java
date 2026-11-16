package app.medreminder.alarm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the alarm needs, kept on the phone so alarms work with no internet:
 * the schedule (from the app), which doses were already handled, and a queue of TAKEN/MISSED logs
 * waiting to be uploaded (Phase 5).
 *
 * Schedule JSON (set by the app through MedAlarmPlugin.setSchedule):
 * { "timeZone": "Asia/Kolkata", "lang": "te-IN", "ui": { labels in the person's language },
 *   "alarms": [ { "id", "kind": "MEDICINE"|"ROUTINE", "time": "07:00", "days": [1..7, Monday = 1],
 *                 "remindBeforeMin", "repeatEveryMin", "stopAfterMin", "title", "subtitle", "chips": [..],
 *                 "spoken", "shape", "color", "photoUri"?, "voiceNoteUri"? } ] }
 * A one-off alarm (the test alarm) has "at" (epoch ms) instead of time/days, and "test": true.
 */
final class AlarmStore {
    static final String STATUS_TAKEN = "TAKEN";
    static final String STATUS_MISSED = "MISSED";

    private static final String PREFS = "med_alarm";
    private static final String KEY_CONFIG = "config";
    private static final String KEY_TEST = "test_alarm";
    private static final String KEY_HANDLED = "handled";
    private static final String KEY_FIRST_SEEN = "first_seen";
    private static final String KEY_LOGS = "pending_logs";
    private static final String KEY_SCHEDULED = "scheduled_ids";
    private static final String KEY_API_BASE = "api_base";
    private static final String KEY_DEVICE_TOKEN = "device_token";
    private static final String KEY_SYNC = "last_sync";
    private static final String KEY_SYNC_AT = "last_sync_at";
    private static final long KEEP_HANDLED_MS = 3L * 24 * 60 * 60 * 1000;

    private AlarmStore() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONObject readObject(Context ctx, String key) {
        try {
            return new JSONObject(prefs(ctx).getString(key, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static JSONArray readArray(Context ctx, String key) {
        try {
            return new JSONArray(prefs(ctx).getString(key, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    // ---- schedule ----

    static synchronized JSONObject config(Context ctx) {
        return readObject(ctx, KEY_CONFIG);
    }

    /** Saves a new schedule. Remembers when each alarm was first seen, so a newly added 7:00 alarm
     *  added at 10:00 is not counted as missed for today. */
    static synchronized void setConfig(Context ctx, JSONObject config) {
        JSONObject seen = readObject(ctx, KEY_FIRST_SEEN);
        JSONObject nextSeen = new JSONObject();
        long now = System.currentTimeMillis();
        JSONArray alarms = config.optJSONArray("alarms");
        try {
            for (int i = 0; alarms != null && i < alarms.length(); i++) {
                String id = alarms.getJSONObject(i).getString("id");
                nextSeen.put(id, seen.optLong(id, now));
            }
        } catch (JSONException ignored) {
            // A malformed alarm is skipped by the scheduler.
        }
        prefs(ctx).edit()
                .putString(KEY_CONFIG, config.toString())
                .putString(KEY_FIRST_SEEN, nextSeen.toString())
                .apply();
    }

    static synchronized long firstSeen(Context ctx, String id) {
        return readObject(ctx, KEY_FIRST_SEEN).optLong(id, System.currentTimeMillis());
    }

    static synchronized void setTestAlarm(Context ctx, JSONObject alarm) {
        prefs(ctx).edit().putString(KEY_TEST, alarm == null ? null : alarm.toString()).apply();
    }

    /** All alarms, including the test alarm if one is set. */
    static synchronized List<JSONObject> alarms(Context ctx) {
        List<JSONObject> list = new ArrayList<>();
        JSONArray arr = config(ctx).optJSONArray("alarms");
        for (int i = 0; arr != null && i < arr.length(); i++) {
            JSONObject a = arr.optJSONObject(i);
            if (a != null && a.has("id")) {
                list.add(a);
            }
        }
        String test = prefs(ctx).getString(KEY_TEST, null);
        if (test != null) {
            try {
                list.add(new JSONObject(test));
            } catch (JSONException ignored) {
                // ignore a broken test alarm
            }
        }
        return list;
    }

    static JSONObject alarm(Context ctx, String id) {
        for (JSONObject a : alarms(ctx)) {
            if (id.equals(a.optString("id"))) {
                return a;
            }
        }
        return null;
    }

    static JSONObject ui(Context ctx) {
        JSONObject ui = config(ctx).optJSONObject("ui");
        return ui != null ? ui : new JSONObject();
    }

    // ---- handled doses ----

    static synchronized boolean isHandled(Context ctx, String occurrenceKey) {
        return readObject(ctx, KEY_HANDLED).has(occurrenceKey);
    }

    /**
     * Marks a dose TAKEN or MISSED once. A later TAKEN replaces MISSED (e.g. after the family replayed the
     * reminder). Returns false if nothing changed.
     */
    static synchronized boolean markHandled(Context ctx, String occurrenceKey, String status) {
        JSONObject handled = readObject(ctx, KEY_HANDLED);
        JSONObject old = handled.optJSONObject(occurrenceKey);
        if (old != null && !(STATUS_TAKEN.equals(status) && STATUS_MISSED.equals(old.optString("status")))) {
            return false;
        }
        long now = System.currentTimeMillis();
        try {
            handled.put(occurrenceKey, new JSONObject().put("status", status).put("at", now));
            // Forget old entries so the store stays small.
            for (Iterator<String> it = handled.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONObject v = handled.optJSONObject(k);
                if (v != null && now - v.optLong("at") > KEEP_HANDLED_MS) {
                    it.remove();
                }
            }
        } catch (JSONException e) {
            return false;
        }
        prefs(ctx).edit().putString(KEY_HANDLED, handled.toString()).apply();
        return true;
    }

    static synchronized boolean isTaken(Context ctx, String occurrenceKey) {
        JSONObject v = readObject(ctx, KEY_HANDLED).optJSONObject(occurrenceKey);
        return v != null && STATUS_TAKEN.equals(v.optString("status"));
    }

    /** Status of every handled dose ("scheduleId@yyyy-MM-dd" → TAKEN/MISSED), for the Today screen. */
    static synchronized JSONObject handledStatuses(Context ctx) {
        JSONObject handled = readObject(ctx, KEY_HANDLED);
        JSONObject out = new JSONObject();
        for (Iterator<String> it = handled.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject v = handled.optJSONObject(k);
            try {
                out.put(k, v == null ? "" : v.optString("status"));
            } catch (JSONException ignored) {
                // skip
            }
        }
        return out;
    }

    // ---- this phone's link to the server (set after pairing) ----

    static synchronized void setDevice(Context ctx, String apiBase, String token) {
        prefs(ctx).edit().putString(KEY_API_BASE, apiBase).putString(KEY_DEVICE_TOKEN, token).apply();
    }

    static String apiBase(Context ctx) {
        return prefs(ctx).getString(KEY_API_BASE, null);
    }

    static String deviceToken(Context ctx) {
        return prefs(ctx).getString(KEY_DEVICE_TOKEN, null);
    }

    /** The last schedule fetched from the server, kept so the Today screen works offline. */
    static synchronized void setSync(Context ctx, JSONObject sync) {
        prefs(ctx).edit().putString(KEY_SYNC, sync.toString()).putLong(KEY_SYNC_AT, System.currentTimeMillis()).apply();
    }

    static synchronized JSONObject sync(Context ctx) {
        String s = prefs(ctx).getString(KEY_SYNC, null);
        try {
            return s == null ? null : new JSONObject(s);
        } catch (JSONException e) {
            return null;
        }
    }

    static long syncAt(Context ctx) {
        return prefs(ctx).getLong(KEY_SYNC_AT, 0);
    }

    // ---- offline log queue (uploaded in Phase 5; each log has a unique id so uploads are idempotent) ----

    static synchronized JSONObject addLog(Context ctx, String scheduleId, String date, String status) {
        JSONArray logs = readArray(ctx, KEY_LOGS);
        JSONObject log = new JSONObject();
        try {
            log.put("id", UUID.randomUUID().toString())
                    .put("scheduleId", scheduleId)
                    .put("date", date)
                    .put("status", status)
                    .put("at", System.currentTimeMillis());
        } catch (JSONException e) {
            return log;
        }
        logs.put(log);
        prefs(ctx).edit().putString(KEY_LOGS, logs.toString()).apply();
        return log;
    }

    static synchronized JSONArray pendingLogs(Context ctx) {
        return readArray(ctx, KEY_LOGS);
    }

    static synchronized void removeLogs(Context ctx, Set<String> ids) {
        JSONArray logs = readArray(ctx, KEY_LOGS);
        JSONArray keep = new JSONArray();
        for (int i = 0; i < logs.length(); i++) {
            JSONObject l = logs.optJSONObject(i);
            if (l != null && !ids.contains(l.optString("id"))) {
                keep.put(l);
            }
        }
        prefs(ctx).edit().putString(KEY_LOGS, keep.toString()).apply();
    }

    // ---- which alarm ids currently have an AlarmManager entry (to cancel removed ones) ----

    static synchronized Set<String> scheduledIds(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet(KEY_SCHEDULED, new HashSet<>()));
    }

    static synchronized void setScheduledIds(Context ctx, Set<String> ids) {
        prefs(ctx).edit().putStringSet(KEY_SCHEDULED, new HashSet<>(ids)).apply();
    }

    static synchronized void clearAll(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }
}
