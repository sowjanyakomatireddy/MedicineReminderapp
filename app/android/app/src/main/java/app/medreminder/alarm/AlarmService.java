package app.medreminder.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import app.medreminder.R;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Locale;

/**
 * Rings one dose: shows the full-screen notification, plays the alarm sound, then speaks the sentence in the
 * person's language with Android Text-to-Speech at alarm volume. Rings for about a minute, then goes quiet
 * until the next repeat (planned by AlarmScheduler). The notification stays, so Taken can still be tapped.
 *
 * If the phone has no voice for the language, it plays the family's recorded voice note when one exists,
 * otherwise only the alarm sound.
 */
public class AlarmService extends Service implements TextToSpeech.OnInitListener {
    static final String ACTION_RING = "app.medreminder.alarm.RING";
    static final String ACTION_SPEAK = "app.medreminder.alarm.SPEAK";
    static final String ACTION_STOP = "app.medreminder.alarm.STOP";
    static final String CHANNEL_ALARM = "medicine_alarm";
    static final String CHANNEL_ROUTINE = "routine_reminder";

    private static final long RING_FOR_MS = 60_000;
    private static final long CHIME_MS = 3_000;
    private static final long[] VIBRATE = {0, 800, 600, 800, 600};
    private static volatile String ringingKey;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean voiceOk;
    private MediaPlayer player;
    private Vibrator vibrator;
    private JSONObject alarm;
    private Runnable pendingSpeech;

    // ---------------------------------------------------------------- entry points

    static void ring(Context ctx, String id, String key, long doseAt) {
        Intent i = new Intent(ctx, AlarmService.class).setAction(ACTION_RING)
                .putExtra(AlarmScheduler.EXTRA_ID, id)
                .putExtra(AlarmScheduler.EXTRA_KEY, key)
                .putExtra(AlarmScheduler.EXTRA_DOSE_AT, doseAt);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i); // allowed: started by an exact alarm-clock alarm
        } else {
            ctx.startService(i);
        }
    }

    static void stopIfRinging(Context ctx, String key) {
        if (key != null && key.equals(ringingKey)) {
            ctx.stopService(new Intent(ctx, AlarmService.class));
            ringingKey = null;
        }
    }

    static int notificationId(String key) {
        return 1000 + (key.hashCode() & 0x0FFFFFFF);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        tts = new TextToSpeech(this, this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        String id = intent == null ? null : intent.getStringExtra(AlarmScheduler.EXTRA_ID);
        JSONObject a = id == null ? null : AlarmStore.alarm(this, id);

        if (ACTION_RING.equals(action) && a != null) {
            String key = intent.getStringExtra(AlarmScheduler.EXTRA_KEY);
            long doseAt = intent.getLongExtra(AlarmScheduler.EXTRA_DOSE_AT, 0);
            stopSound();
            alarm = a;
            ringingKey = key;
            Notification n = buildNotification(this, a, key, doseAt);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(notificationId(key), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(notificationId(key), n);
            }
            startRinging();
            handler.postDelayed(this::goQuiet, RING_FOR_MS);
        } else if (ACTION_SPEAK.equals(action) && a != null) {
            alarm = a;
            stopSound(); // also clears the pending "go quiet", so re-arm it below
            whenVoiceReady(() -> speak(1));
            handler.postDelayed(ringingKey != null ? this::goQuiet : this::stopSelfIfIdle, 30_000);
        } else if (ACTION_STOP.equals(action)) {
            stopSelf();
        } else if (alarm == null) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopSound();
        if (tts != null) {
            tts.shutdown();
        }
        ringingKey = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------------------------------------------------------------- sound and voice

    @Override
    public void onInit(int status) {
        ttsReady = true;
        voiceOk = false;
        if (status == TextToSpeech.SUCCESS) {
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            tts.setSpeechRate(0.9f);
            String lang = AlarmStore.config(this).optString("lang", "te-IN");
            int r = tts.setLanguage(Locale.forLanguageTag(lang));
            voiceOk = r >= TextToSpeech.LANG_AVAILABLE;
        }
        if (pendingSpeech != null) {
            Runnable p = pendingSpeech;
            pendingSpeech = null;
            p.run();
        }
    }

    private void whenVoiceReady(Runnable r) {
        if (ttsReady) {
            r.run();
        } else {
            pendingSpeech = r;
        }
    }

    private boolean calm() {
        return alarm != null && "ROUTINE".equals(alarm.optString("kind"));
    }

    /** Medicine: vibrate + alarm sound, then the sentence three times. Routine (calmer): the sentence twice. */
    private void startRinging() {
        if (calm()) {
            whenVoiceReady(() -> speak(2));
            return;
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(VIBRATE, 1),
                        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            } else {
                vibrator.vibrate(VIBRATE, 1);
            }
        }
        playUri(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), false);
        handler.postDelayed(() -> {
            stopPlayer();
            whenVoiceReady(() -> speak(3));
        }, CHIME_MS);
    }

    private void speak(int times) {
        if (alarm == null) {
            return;
        }
        String text = alarm.optString("spoken", alarm.optString("title"));
        if (voiceOk) {
            for (int i = 0; i < times; i++) {
                tts.speak(text, i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, "say" + i);
                tts.playSilentUtterance(3_000, TextToSpeech.QUEUE_ADD, "gap" + i);
            }
            return;
        }
        String note = alarm.optString("voiceNoteUri", "");
        if (!note.isEmpty()) {
            playUri(Uri.parse(note), false);
        } else if (!calm()) {
            playUri(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), true);
        }
        Log.i(AlarmScheduler.TAG, "No Text-to-Speech voice for this language; used fallback sound");
    }

    private void playUri(Uri uri, boolean loop) {
        stopPlayer();
        if (uri == null) {
            return;
        }
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(loop);
            player.prepare();
            player.start();
        } catch (Exception e) {
            Log.w(AlarmScheduler.TAG, "Could not play alarm sound: " + e.getClass().getSimpleName());
            stopPlayer();
        }
    }

    private void stopPlayer() {
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
                // not started
            }
            player.release();
            player = null;
        }
    }

    private void stopSound() {
        handler.removeCallbacksAndMessages(null);
        stopPlayer();
        if (vibrator != null) {
            vibrator.cancel();
        }
        if (tts != null) {
            tts.stop();
        }
    }

    /** After a minute of ringing: go quiet but leave the notification so Taken still works. */
    private void goQuiet() {
        stopSound();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH);
        } else {
            stopForeground(false);
        }
        ringingKey = null;
        stopSelf();
    }

    private void stopSelfIfIdle() {
        if (ringingKey == null) {
            stopSelf();
        }
    }

    // ---------------------------------------------------------------- notification

    static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel alarm = new NotificationChannel(CHANNEL_ALARM,
                ctx.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH);
        alarm.setSound(null, null); // the service plays the sound and voice itself, at alarm volume
        alarm.enableVibration(false);
        alarm.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationChannel routine = new NotificationChannel(CHANNEL_ROUTINE,
                ctx.getString(R.string.channel_routine), NotificationManager.IMPORTANCE_HIGH);
        routine.setSound(null, null);
        routine.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(alarm);
        nm.createNotificationChannel(routine);
    }

    static Notification buildNotification(Context ctx, JSONObject a, String key, long doseAt) {
        createChannels(ctx);
        JSONObject ui = AlarmStore.ui(ctx);
        boolean routine = "ROUTINE".equals(a.optString("kind"));
        Intent show = new Intent(ctx, AlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(AlarmScheduler.EXTRA_ID, a.optString("id"))
                .putExtra(AlarmScheduler.EXTRA_KEY, key)
                .putExtra(AlarmScheduler.EXTRA_DOSE_AT, doseAt);
        PendingIntent full = PendingIntent.getActivity(ctx, key.hashCode(), show,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent takenIntent = new Intent(ctx, AlarmReceiver.class).setAction(AlarmReceiver.ACTION_TAKEN)
                .setData(Uri.parse("medalarm://taken/" + Uri.encode(key)))
                .putExtra(AlarmScheduler.EXTRA_ID, a.optString("id"))
                .putExtra(AlarmScheduler.EXTRA_KEY, key)
                .putExtra(AlarmScheduler.EXTRA_DOSE_AT, doseAt);
        PendingIntent taken = PendingIntent.getBroadcast(ctx, key.hashCode(), takenIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(doseAt);
        String heading = ui.optString(routine ? "routineTime" : "medicineTime",
                ctx.getString(routine ? R.string.routine_time : R.string.medicine_time));
        return new NotificationCompat.Builder(ctx, routine ? CHANNEL_ROUTINE : CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(heading + " · " + time)
                .setContentText(a.optString("title"))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(a.optString("spoken", a.optString("title"))))
                .setCategory(routine ? NotificationCompat.CATEGORY_REMINDER : NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .addAction(0, ui.optString("taken", ctx.getString(R.string.taken)), taken)
                .build();
    }
}
