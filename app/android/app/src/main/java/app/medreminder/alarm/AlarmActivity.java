package app.medreminder.alarm;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import app.medreminder.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * The full-screen alarm over the lock screen (prototype screen "Alarm"): time, a picture of the tablet,
 * the name in the person's language, food and look chips, what the voice says, a very large Taken button,
 * and Listen again. All words come from the app in the person's language (AlarmStore "ui").
 */
public class AlarmActivity extends AppCompatActivity {
    private static final Map<String, Integer> COLOURS = new HashMap<>();

    static {
        COLOURS.put("WHITE", 0xFFFFFFFF);
        COLOURS.put("YELLOW", 0xFFF2C230);
        COLOURS.put("ORANGE", 0xFFF28C28);
        COLOURS.put("PINK", 0xFFF4A6C0);
        COLOURS.put("RED", 0xFFC8364B);
        COLOURS.put("BLUE", 0xFF4A7BD0);
        COLOURS.put("GREEN", 0xFF4FA36B);
        COLOURS.put("BROWN", 0xFF8B5A2B);
        COLOURS.put("MIXED", 0xFFC8364B);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private JSONObject alarm;
    private String key;
    private long doseAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_alarm);
        show(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        show(intent);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void show(Intent intent) {
        String id = intent.getStringExtra(AlarmScheduler.EXTRA_ID);
        key = intent.getStringExtra(AlarmScheduler.EXTRA_KEY);
        doseAt = intent.getLongExtra(AlarmScheduler.EXTRA_DOSE_AT, System.currentTimeMillis());
        alarm = id == null ? null : AlarmStore.alarm(this, id);
        // Already taken: nothing to do. (A MISSED dose can still be shown: the family replayed the reminder.)
        if (alarm == null || key == null || AlarmStore.isTaken(this, key)) {
            finish();
            return;
        }
        JSONObject ui = AlarmStore.ui(this);
        boolean routine = "ROUTINE".equals(alarm.optString("kind"));

        findViewById(R.id.alarm_content).setVisibility(View.VISIBLE);
        findViewById(R.id.done).setVisibility(View.GONE);
        text(R.id.label, ui.optString(routine ? "routineTime" : "medicineTime",
                getString(routine ? R.string.routine_time : R.string.medicine_time)));
        text(R.id.time, DateFormat.getTimeInstance(DateFormat.SHORT).format(doseAt));
        text(R.id.period, ui.optString(period(doseAt), ""));
        text(R.id.title, alarm.optString("title"));
        text(R.id.subtitle, alarm.optString("subtitle"));
        text(R.id.voice_label, ui.optString("voiceSays", getString(R.string.voice_says)));
        text(R.id.spoken, "“" + alarm.optString("spoken", alarm.optString("title")) + "”");
        // Routine and period reminders are not medicines: no tablet picture or medicine chips, so they
        // can't be mistaken for a dose.
        findViewById(R.id.picture).setVisibility(routine ? View.GONE : View.VISIBLE);
        if (routine) {
            ((LinearLayout) findViewById(R.id.chips)).removeAllViews();
            findViewById(R.id.chips).setVisibility(View.GONE);
        } else {
            showPicture();
            showChips();
        }

        Button taken = findViewById(R.id.btn_taken);
        if (routine) {
            // A routine reminder just needs "OK"; there is nothing to record.
            taken.setText(ui.optString("ok", getString(R.string.ok)));
            taken.setOnClickListener(v -> {
                AlarmReceiver.taken(this, alarm, key, doseAt);
                finish();
            });
        } else {
            taken.setText("✓  " + ui.optString("taken", getString(R.string.taken)));
            taken.setOnClickListener(v -> onTaken(ui));
        }
        Button listen = findViewById(R.id.btn_listen);
        listen.setText(ui.optString("listenAgain", getString(R.string.listen_again)));
        listen.setOnClickListener(v -> startService(new Intent(this, AlarmService.class)
                .setAction(AlarmService.ACTION_SPEAK)
                .putExtra(AlarmScheduler.EXTRA_ID, alarm.optString("id"))));
    }

    /** The Taken screen: well done, what was taken, the next medicine, and whether the family knows. */
    private void onTaken(JSONObject ui) {
        AlarmReceiver.taken(this, alarm, key, doseAt);
        findViewById(R.id.alarm_content).setVisibility(View.GONE);
        findViewById(R.id.done).setVisibility(View.VISIBLE);
        text(R.id.done_title, ui.optString("takenTitle", getString(R.string.taken_title)));
        text(R.id.done_text, ui.optString("takenLine", getString(R.string.taken_done))
                .replace("{{name}}", alarm.optString("title")));
        showNext(ui);
        TextView family = findViewById(R.id.family_text);
        family.setText(ui.optString("familyLater", getString(R.string.family_later)));
        SyncClient.uploadAsync(this, () -> runOnUiThread(() ->
                family.setText("✓ " + ui.optString("familyInformed", getString(R.string.family_informed)))));
        Button ok = findViewById(R.id.btn_ok);
        ok.setText(ui.optString("ok", getString(R.string.ok)));
        ok.setOnClickListener(v -> finish());
        handler.postDelayed(this::finish, 60_000);
    }

    /** "Next medicine: 1:30 PM afternoon · Sugar tablet", from the planned alarms. */
    private void showNext(JSONObject ui) {
        long now = System.currentTimeMillis();
        AlarmScheduler.Event best = null;
        JSONObject bestAlarm = null;
        for (JSONObject a : AlarmStore.alarms(this)) {
            if (a.optBoolean("test") || "ROUTINE".equals(a.optString("kind"))) continue;
            AlarmScheduler.Event e = AlarmScheduler.next(this, a, now);
            if (e != null && AlarmScheduler.TYPE_RING.equals(e.type) && (best == null || e.doseAt < best.doseAt)) {
                best = e;
                bestAlarm = a;
            }
        }
        View box = findViewById(R.id.next_box);
        if (best == null) {
            box.setVisibility(View.GONE);
            return;
        }
        box.setVisibility(View.VISIBLE);
        text(R.id.next_label, ui.optString("nextMedicine", getString(R.string.next_medicine)));
        String when = DateFormat.getTimeInstance(DateFormat.SHORT).format(best.doseAt) + " "
                + ui.optString(period(best.doseAt), "");
        text(R.id.next_text, when.trim() + " · " + bestAlarm.optString("title"));
    }

    private void text(int viewId, String value) {
        TextView v = findViewById(viewId);
        v.setText(value);
        v.setVisibility(value == null || value.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** "morning" / "afternoon" / "evening" / "night", looked up in the ui labels. */
    private String period(long at) {
        Calendar c = Calendar.getInstance(TimeZone.getDefault());
        c.setTimeInMillis(at);
        int h = c.get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 12) return "morning";
        if (h >= 12 && h < 17) return "afternoon";
        if (h >= 17 && h < 21) return "evening";
        return "night";
    }

    /** The family's photo if the app saved one on the phone; otherwise a drawing from shape and colour. */
    private void showPicture() {
        ImageView img = findViewById(R.id.picture);
        String photo = alarm.optString("photoUri", "");
        if (!photo.isEmpty()) {
            img.setImageURI(Uri.parse(photo));
            if (img.getDrawable() != null) {
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                img.setClipToOutline(true);
                return;
            }
        }
        float dp = getResources().getDisplayMetrics().density;
        GradientDrawable pill = new GradientDrawable();
        Integer fill = COLOURS.get(alarm.optString("color", "WHITE"));
        pill.setColor(fill == null ? Color.WHITE : fill);
        pill.setStroke(Math.round(3 * dp), 0xFF14262B);
        String shape = alarm.optString("shape", "ROUND");
        if ("CAPSULE".equals(shape)) {
            pill.setShape(GradientDrawable.RECTANGLE);
            pill.setCornerRadius(24 * dp);
            pill.setSize(Math.round(100 * dp), Math.round(48 * dp));
        } else if ("OVAL".equals(shape)) {
            pill.setShape(GradientDrawable.OVAL);
            pill.setSize(Math.round(100 * dp), Math.round(60 * dp));
        } else if ("OTHER".equals(shape)) {
            pill.setShape(GradientDrawable.RECTANGLE);
            pill.setCornerRadius(14 * dp);
            pill.setSize(Math.round(72 * dp), Math.round(72 * dp));
        } else {
            pill.setShape(GradientDrawable.OVAL);
            pill.setSize(Math.round(88 * dp), Math.round(88 * dp));
        }
        img.setScaleType(ImageView.ScaleType.CENTER);
        img.setImageDrawable(pill);
    }

    /** Chips such as "భోజనానికి ముందు" (before food) and "గుండ్రని · తెల్లని" (round · white). */
    private void showChips() {
        LinearLayout box = findViewById(R.id.chips);
        box.removeAllViews();
        JSONArray chips = alarm.optJSONArray("chips");
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; chips != null && i < chips.length(); i++) {
            JSONObject c = chips.optJSONObject(i);
            if (c == null) continue;
            TextView t = new TextView(this);
            t.setText(c.optString("text"));
            t.setTextSize(20);
            t.setPadding(Math.round(16 * dp), Math.round(8 * dp), Math.round(16 * dp), Math.round(8 * dp));
            String tone = c.optString("tone", "grey");
            int bg = R.drawable.bg_chip_grey;
            int fg = 0xFF14262B;
            if ("before".equals(tone)) {
                bg = R.drawable.bg_chip_before;
                fg = 0xFF6B3900;
            } else if ("after".equals(tone)) {
                bg = R.drawable.bg_chip_after;
                fg = 0xFF234D1B;
            }
            t.setBackgroundResource(bg);
            t.setTextColor(fg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(Math.round(5 * dp), Math.round(5 * dp), Math.round(5 * dp), Math.round(5 * dp));
            box.addView(t, lp);
        }
        box.setVisibility(box.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }
}
