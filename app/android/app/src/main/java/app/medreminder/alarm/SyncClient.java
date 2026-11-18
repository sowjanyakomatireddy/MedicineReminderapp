package app.medreminder.alarm;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Talks to the server with the phone's device token, natively, so it also works when the app is closed
 * (after a SYNC push, the daily sync, or a restart):
 *  1. upload the offline TAKEN/MISSED queue;
 *  2. fetch the schedule, apply doses the family already answered, download medicine photos;
 *  3. rebuild and re-plan the alarms.
 */
public final class SyncClient {
    private static final String TAG = AlarmScheduler.TAG;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final long DAY = 24L * 60 * 60 * 1000;

    /** Thrown when the server says this phone is no longer linked. */
    static final class UnlinkedException extends IOException {
        UnlinkedException() {
            super("unlinked");
        }
    }

    private SyncClient() {
    }

    /** Runs a full sync in the background. */
    public static void syncAsync(Context ctx) {
        Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            try {
                sync(app);
            } catch (Exception e) {
                Log.i(TAG, "Sync skipped: " + e.getClass().getSimpleName());
            }
        });
    }

    /** Uploads the offline queue in the background (after Taken / Missed). */
    static void uploadAsync(Context ctx, Runnable onSuccess) {
        Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            try {
                if (uploadLogs(app) && onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                Log.i(TAG, "Upload later: " + e.getClass().getSimpleName());
            }
        });
    }

    /** Blocking full sync. Returns the sync data, or null if the phone is not linked yet. */
    static synchronized JSONObject sync(Context ctx) throws IOException, JSONException {
        if (AlarmStore.deviceToken(ctx) == null) {
            return null;
        }
        try {
            uploadLogs(ctx);
            JSONObject sync = new JSONObject(request(ctx, "GET", "/api/devices/me/sync", null));
            applyAnswers(ctx, sync.optJSONArray("logs"));
            Map<String, String> photos = downloadPhotos(ctx, sync.optJSONArray("medicines"));
            JSONObject config = AlarmStore.config(ctx);
            JSONObject templates = config.optJSONObject("templates");
            config.put("alarms", AlarmBuilder.build(sync, templates == null ? new JSONObject() : templates, photos));
            JSONObject person = sync.optJSONObject("person");
            if (person != null) {
                config.put("timeZone", person.optString("timeZone", config.optString("timeZone", "Asia/Kolkata")));
            }
            AlarmStore.setConfig(ctx, config);
            AlarmStore.setSync(ctx, sync);
            AlarmScheduler.rescheduleAll(ctx);
            registerPushToken(ctx);
            MedAlarmPlugin.emitSynced();
            return sync;
        } catch (UnlinkedException e) {
            // The family unlinked this phone: stop ringing for them. The app shows the code screen.
            for (String id : AlarmStore.scheduledIds(ctx)) {
                AlarmScheduler.cancel(ctx, id);
            }
            AlarmStore.clearAll(ctx);
            throw e;
        }
    }

    /** Uploads queued logs; removes the ones the server accepted. Returns true if the queue is now empty. */
    static synchronized boolean uploadLogs(Context ctx) throws IOException, JSONException {
        JSONArray logs = AlarmStore.pendingLogs(ctx);
        if (logs.length() == 0) {
            return true;
        }
        JSONObject res = new JSONObject(request(ctx, "POST", "/api/devices/me/dose-logs", logs.toString()));
        JSONArray accepted = res.optJSONArray("accepted");
        Set<String> ids = new HashSet<>();
        for (int i = 0; accepted != null && i < accepted.length(); i++) {
            ids.add(accepted.optString(i));
        }
        AlarmStore.removeLogs(ctx, ids);
        return AlarmStore.pendingLogs(ctx).length() == 0;
    }

    /** Doses the family marked taken (or the server marked missed) should not ring. Stops one ringing now. */
    private static void applyAnswers(Context ctx, JSONArray logs) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        for (int i = 0; logs != null && i < logs.length(); i++) {
            JSONObject l = logs.optJSONObject(i);
            if (l == null) continue;
            String key = l.optString("scheduleId") + "@" + l.optString("date");
            if (AlarmStore.markHandled(ctx, key, l.optString("status"))) {
                AlarmService.stopIfRinging(ctx, key);
                nm.cancel(AlarmService.notificationId(key));
            }
        }
    }

    /**
     * The photos already on the phone for these medicines (medicineId → file URI), without downloading.
     * Used by the Today screen; a photo not downloaded yet is simply left out (the drawing is shown).
     */
    static Map<String, String> localPhotos(Context ctx, JSONArray meds) {
        Map<String, String> out = new HashMap<>();
        File dir = new File(ctx.getFilesDir(), "photos");
        for (int i = 0; meds != null && i < meds.length(); i++) {
            JSONObject m = meds.optJSONObject(i);
            if (m == null || !m.optBoolean("hasPhoto")) continue;
            String id = m.optString("id");
            File f = new File(dir, id + "_" + m.optString("photoVersion", "v") + ".img");
            if (f.isFile()) out.put(id, Uri.fromFile(f).toString());
        }
        return out;
    }

    /** Keeps one local copy per medicine photo version, for the alarm screen offline. */
    private static Map<String, String> downloadPhotos(Context ctx, JSONArray meds) {
        Map<String, String> out = new HashMap<>();
        File dir = new File(ctx.getFilesDir(), "photos");
        if (!dir.exists() && !dir.mkdirs()) {
            return out;
        }
        Set<String> keep = new HashSet<>();
        for (int i = 0; meds != null && i < meds.length(); i++) {
            JSONObject m = meds.optJSONObject(i);
            if (m == null || !m.optBoolean("hasPhoto")) continue;
            String id = m.optString("id");
            File f = new File(dir, id + "_" + m.optString("photoVersion", "v") + ".img");
            if (!f.exists()) {
                try {
                    byte[] data = requestBytes(ctx, "/api/devices/me/medicines/" + id + "/photo");
                    try (OutputStream os = new FileOutputStream(f)) {
                        os.write(data);
                    }
                } catch (IOException e) {
                    continue; // try again next sync; the drawing is shown meanwhile
                }
            }
            keep.add(f.getName());
            out.put(id, Uri.fromFile(f).toString());
        }
        File[] files = dir.listFiles();
        for (int i = 0; files != null && i < files.length; i++) {
            if (!keep.contains(files[i].getName())) {
                //noinspection ResultOfMethodCallIgnored
                files[i].delete();
            }
        }
        return out;
    }

    /** Sends this phone's Firebase token to the server, if Firebase is set up in this build. */
    static void registerPushToken(Context ctx) {
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            return; // no google-services.json in this build: push is off, daily sync still runs
        }
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> uploadToken(ctx, token));
    }

    static void uploadToken(Context ctx, String token) {
        IO.execute(() -> {
            try {
                request(ctx, "PUT", "/api/devices/me/push-token", new JSONObject().put("token", token).toString());
            } catch (Exception e) {
                Log.i(TAG, "Push token upload later");
            }
        });
    }

    // ---------------------------------------------------------------- daily sync

    /** A loose daily sync (inexact, battery friendly), as a safety net when pushes don't arrive. */
    static void scheduleDaily(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 7, new Intent(ctx, SyncReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + DAY, AlarmManager.INTERVAL_DAY, pi);
    }

    // ---------------------------------------------------------------- HTTP

    private static String request(Context ctx, String method, String path, String body) throws IOException {
        HttpURLConnection c = open(ctx, path);
        c.setRequestMethod(method);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        return new String(read(c), StandardCharsets.UTF_8);
    }

    private static byte[] requestBytes(Context ctx, String path) throws IOException {
        return read(open(ctx, path));
    }

    private static HttpURLConnection open(Context ctx, String path) throws IOException {
        String base = AlarmStore.apiBase(ctx);
        String token = AlarmStore.deviceToken(ctx);
        if (base == null || token == null) {
            throw new IOException("not linked");
        }
        HttpURLConnection c = (HttpURLConnection) new URL(base.replaceAll("/+$", "") + path).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(15_000);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/json");
        return c;
    }

    private static byte[] read(HttpURLConnection c) throws IOException {
        int code = c.getResponseCode();
        if (code == 401) {
            throw new UnlinkedException();
        }
        if (code >= 300) {
            throw new IOException("HTTP " + code);
        }
        if (code == 204) {
            return new byte[0];
        }
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    /** For the Today screen: today's statuses as the phone knows them (including not-yet-uploaded ones). */
    static JSONObject localStatuses(Context ctx) {
        JSONObject handled = AlarmStore.handledStatuses(ctx);
        JSONObject out = new JSONObject();
        for (Iterator<String> it = handled.keys(); it.hasNext(); ) {
            String k = it.next();
            try {
                out.put(k, handled.optString(k));
            } catch (JSONException ignored) {
                // skip
            }
        }
        return out;
    }
}
