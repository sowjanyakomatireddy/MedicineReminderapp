package app.medreminder.alarm;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.speech.tts.TextToSpeech;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The bridge between the React app (src/native/medAlarm.ts) and the native alarm.
 * The app builds the schedule and every sentence in the person's language; this side stores it,
 * rings it, and keeps a queue of TAKEN / MISSED logs for the app to upload.
 */
@CapacitorPlugin(
        name = "MedAlarm",
        permissions = @Permission(alias = "notifications", strings = {Manifest.permission.POST_NOTIFICATIONS}))
public class MedAlarmPlugin extends Plugin {
    private static MedAlarmPlugin instance;

    @Override
    public void load() {
        instance = this;
        AlarmService.createChannels(getContext());
    }

    /** Lets the app react at once when a dose is taken or missed while it is open. */
    static void emitDoseLogged(JSONObject log) {
        MedAlarmPlugin p = instance;
        if (p != null) {
            try {
                p.notifyListeners("doseLogged", new JSObject(log.toString()));
            } catch (JSONException ignored) {
                // not important: the log is in the queue anyway
            }
        }
    }

    // ---------------------------------------------------------------- schedule

    /** { timeZone, lang, ui, alarms } — replaces the whole schedule and re-plans every alarm. */
    @PluginMethod
    public void setSchedule(PluginCall call) {
        JSONObject config = call.getData();
        if (config.optJSONArray("alarms") == null) {
            call.reject("alarms is required");
            return;
        }
        AlarmStore.setConfig(getContext(), config);
        AlarmScheduler.rescheduleAll(getContext());
        call.resolve(scheduled());
    }

    /**
     * { timeZone, lang, ui, templates } — who this phone belongs to and every word the alarm needs, in the
     * person's language, without changing the alarm list. The templates let the native side build alarms
     * after a background sync (see AlarmBuilder).
     */
    @PluginMethod
    public void setProfile(PluginCall call) {
        JSONObject config = AlarmStore.config(getContext());
        try {
            config.put("timeZone", call.getString("timeZone", config.optString("timeZone", "Asia/Kolkata")));
            config.put("lang", call.getString("lang", "te-IN"));
            JSObject ui = call.getObject("ui");
            if (ui != null) config.put("ui", ui);
            JSObject templates = call.getObject("templates");
            if (templates != null) config.put("templates", templates);
            if (config.optJSONArray("alarms") == null) config.put("alarms", new JSONArray());
        } catch (JSONException e) {
            call.reject("bad profile");
            return;
        }
        AlarmStore.setConfig(getContext(), config);
        AlarmScheduler.rescheduleAll(getContext());
        call.resolve();
    }

    /** { apiBase, token } — after pairing: lets the native side sync and upload on its own. */
    @PluginMethod
    public void setDevice(PluginCall call) {
        String base = call.getString("apiBase");
        String token = call.getString("token");
        if (base == null || base.isEmpty() || token == null) {
            call.reject("apiBase and token are required");
            return;
        }
        AlarmStore.setDevice(getContext(), base, token);
        SyncClient.scheduleDaily(getContext());
        call.resolve();
    }

    /** Fetches the schedule now (and uploads the queue). Resolves with the sync data; rejects "UNLINKED" or "OFFLINE". */
    @PluginMethod
    public void syncNow(PluginCall call) {
        new Thread(() -> {
            try {
                JSONObject sync = SyncClient.sync(getContext());
                call.resolve(syncData(sync));
            } catch (SyncClient.UnlinkedException e) {
                call.reject("This phone is no longer linked", "UNLINKED");
            } catch (Exception e) {
                call.reject("Could not reach the server", "OFFLINE");
            }
        }).start();
    }

    /** The last sync stored on the phone (for the Today screen when offline), with local statuses. */
    @PluginMethod
    public void getSyncData(PluginCall call) {
        call.resolve(syncData(AlarmStore.sync(getContext())));
    }

    private JSObject syncData(JSONObject sync) {
        JSObject r = new JSObject();
        try {
            if (sync != null) {
                r.put("sync", new JSObject(sync.toString()));
                r.put("photos", new JSObject(new JSONObject(
                        SyncClient.localPhotos(getContext(), sync.optJSONArray("medicines"))).toString()));
            }
            r.put("syncedAt", AlarmStore.syncAt(getContext()));
            r.put("statuses", new JSObject(AlarmStore.handledStatuses(getContext()).toString()));
            r.put("pending", AlarmStore.pendingLogs(getContext()).length());
        } catch (JSONException ignored) {
            // partial data is fine
        }
        return r;
    }

    static void emitSynced() {
        MedAlarmPlugin p = instance;
        if (p != null) p.notifyListeners("synced", new JSObject());
    }

    @PluginMethod
    public void getScheduled(PluginCall call) {
        call.resolve(scheduled());
    }

    private JSObject scheduled() {
        JSObject r = new JSObject();
        try {
            r.put("alarms", new JSArray(AlarmScheduler.describe(getContext()).toString()));
        } catch (JSONException e) {
            r.put("alarms", new JSArray());
        }
        return r;
    }

    /**
     * Debug: one alarm that rings in `seconds` (default 10). Repeats every `repeatEveryMin` (default 1)
     * and stops after `stopAfterMin` (default 3), so the whole cycle can be checked in a few minutes.
     * Test alarms never create dose logs.
     */
    @PluginMethod
    public void testAlarm(PluginCall call) {
        int seconds = call.getInt("seconds", 10);
        JSONObject a = new JSONObject();
        try {
            a.put("id", "test")
                    .put("test", true)
                    .put("kind", call.getString("kind", "MEDICINE"))
                    .put("at", System.currentTimeMillis() + seconds * 1000L)
                    .put("remindBeforeMin", 0)
                    .put("repeatEveryMin", call.getInt("repeatEveryMin", 1))
                    .put("stopAfterMin", call.getInt("stopAfterMin", 3))
                    .put("title", call.getString("title", "Test"))
                    .put("subtitle", call.getString("subtitle", ""))
                    .put("spoken", call.getString("spoken", "Test"))
                    .put("shape", call.getString("shape", "ROUND"))
                    .put("color", call.getString("color", "WHITE"));
            JSArray chips = call.getArray("chips");
            if (chips != null) {
                a.put("chips", new JSONArray(chips.toString()));
            }
        } catch (JSONException e) {
            call.reject("bad test alarm");
            return;
        }
        AlarmStore.setTestAlarm(getContext(), a);
        AlarmScheduler.rescheduleAll(getContext());
        call.resolve(scheduled());
    }

    /** Unlinked or signed out: stop every alarm and forget the schedule. */
    @PluginMethod
    public void cancelAll(PluginCall call) {
        for (String id : AlarmStore.scheduledIds(getContext())) {
            AlarmScheduler.cancel(getContext(), id);
        }
        AlarmStore.clearAll(getContext());
        call.resolve();
    }

    // ---------------------------------------------------------------- dose log queue

    @PluginMethod
    public void getPendingLogs(PluginCall call) {
        JSObject r = new JSObject();
        try {
            r.put("logs", new JSArray(AlarmStore.pendingLogs(getContext()).toString()));
        } catch (JSONException e) {
            r.put("logs", new JSArray());
        }
        call.resolve(r);
    }

    /** { ids: [...] } — remove logs the server has accepted. */
    @PluginMethod
    public void ackLogs(PluginCall call) {
        JSArray ids = call.getArray("ids");
        Set<String> set = new HashSet<>();
        for (int i = 0; ids != null && i < ids.length(); i++) {
            set.add(ids.optString(i));
        }
        AlarmStore.removeLogs(getContext(), set);
        call.resolve();
    }

    // ---------------------------------------------------------------- phone setup

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject s = PhoneSetup.status(getContext());
        if (Build.VERSION.SDK_INT >= 33) {
            s.put("notificationPermission", getPermissionState("notifications").toString().toLowerCase(Locale.ROOT));
        }
        call.resolve(s);
    }

    @PluginMethod
    public void requestNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33 || getPermissionState("notifications") == PermissionState.GRANTED) {
            call.resolve(PhoneSetup.status(getContext()));
        } else {
            requestPermissionForAlias("notifications", call, "notificationsResult");
        }
    }

    @PermissionCallback
    private void notificationsResult(PluginCall call) {
        call.resolve(PhoneSetup.status(getContext()));
    }

    /** { target: notifications | exactAlarm | fullScreen | battery | autostart | lockScreen | app } */
    @PluginMethod
    public void openSettings(PluginCall call) {
        JSObject r = new JSObject();
        r.put("opened", PhoneSetup.open(getContext(), call.getString("target", "app")));
        call.resolve(r);
    }

    // ---------------------------------------------------------------- voice

    /**
     * { lang: "te-IN" } → { status: available | missingData | notSupported | noEngine }.
     * Used before the person's language is relied on, and on the setup screen.
     */
    @PluginMethod
    public void checkVoice(PluginCall call) {
        String lang = call.getString("lang", "te-IN");
        withTts(call, tts -> {
            int r = tts.isLanguageAvailable(Locale.forLanguageTag(lang));
            String status = r >= TextToSpeech.LANG_AVAILABLE ? "available"
                    : r == TextToSpeech.LANG_MISSING_DATA ? "missingData" : "notSupported";
            JSObject o = new JSObject();
            o.put("status", status);
            o.put("engine", tts.getDefaultEngine());
            call.resolve(o);
        });
    }

    /** Opens the system screen to download voice data (e.g. the Telugu voice pack). */
    @PluginMethod
    public void installVoice(PluginCall call) {
        Intent i = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        JSObject r = new JSObject();
        try {
            getContext().startActivity(i);
            r.put("opened", true);
        } catch (ActivityNotFoundException e) {
            r.put("opened", false); // no voice engine that can install data; the app shows a message
        }
        call.resolve(r);
    }

    /** { text, lang } — speak once at alarm volume (the "listen" buttons). */
    @PluginMethod
    public void speak(PluginCall call) {
        String text = call.getString("text", "");
        String lang = call.getString("lang", "te-IN");
        withTts(call, tts -> {
            int r = tts.setLanguage(Locale.forLanguageTag(lang));
            if (r < TextToSpeech.LANG_AVAILABLE) {
                call.reject("No voice for " + lang, "NO_VOICE");
                tts.shutdown();
                return;
            }
            tts.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build());
            tts.setSpeechRate(0.9f);
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { tts.shutdown(); }
                @Override public void onError(String id) { tts.shutdown(); }
            });
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview");
            call.resolve();
        }, false);
    }

    private interface TtsUse {
        void use(TextToSpeech tts);
    }

    private void withTts(PluginCall call, TtsUse use) {
        withTts(call, use, true);
    }

    /** Starts a TextToSpeech engine, runs `use` once it is ready, and shuts it down afterwards if asked. */
    private void withTts(PluginCall call, TtsUse use, boolean shutdownAfter) {
        Context ctx = getContext();
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(ctx, status -> {
            if (status != TextToSpeech.SUCCESS) {
                JSObject o = new JSObject();
                o.put("status", "noEngine");
                call.resolve(o);
                holder[0].shutdown();
                return;
            }
            use.use(holder[0]);
            if (shutdownAfter) {
                holder[0].shutdown();
            }
        });
    }
}
