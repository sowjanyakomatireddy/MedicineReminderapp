package app.medreminder.alarm;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * Turns the server's sync data into alarms, using the sentence templates the app stored in the person's
 * language (AlarmStore config "templates", set by MedAlarmPlugin.setProfile). Runs natively so a SYNC push
 * can update the alarms while the app is closed.
 *
 * templates = { sentence: "{{person}}, ఇది {{voiceName}}. {{foodPhrase}}.",
 *               foodPhrase: {BEFORE, AFTER, ANY}, food: {BEFORE, AFTER, ANY},
 *               shape: {ROUND, ...}, colour: {WHITE, ...} }
 */
final class AlarmBuilder {
    private static final String[] DAYS = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};

    private AlarmBuilder() {
    }

    /** @param photos medicineId → local file URI of its photo (may be empty) */
    static JSONArray build(JSONObject sync, JSONObject templates, Map<String, String> photos) throws JSONException {
        JSONArray alarms = new JSONArray();
        JSONObject person = sync.optJSONObject("person");
        String personName = person == null ? "" : person.optString("name");
        JSONArray meds = sync.optJSONArray("medicines");
        for (int i = 0; meds != null && i < meds.length(); i++) {
            JSONObject m = meds.getJSONObject(i);
            String name = m.optString("name");
            String voiceName = m.optString("voiceName", "");
            String title = voiceName.isEmpty() ? name : voiceName;
            String notes = m.optString("notes", "");
            StringBuilder subtitle = new StringBuilder(voiceName.isEmpty() ? "" : name);
            if (!notes.isEmpty()) {
                subtitle.append(subtitle.length() > 0 ? " · " : "").append(notes);
            }
            JSONArray schedules = m.optJSONArray("schedules");
            for (int j = 0; schedules != null && j < schedules.length(); j++) {
                JSONObject s = schedules.getJSONObject(j);
                if (!s.optBoolean("active", true)) {
                    continue;
                }
                String food = s.optString("food", "ANY");
                JSONObject a = new JSONObject()
                        .put("id", s.getString("id"))
                        .put("kind", "MEDICINE")
                        .put("time", s.getString("time"))
                        .put("days", isoDays(s.optJSONArray("days")))
                        .put("remindBeforeMin", s.optInt("remindBeforeMin", 5))
                        .put("repeatEveryMin", s.optInt("repeatEveryMin", 5))
                        .put("stopAfterMin", s.optInt("stopAfterMin", 30))
                        .put("title", title)
                        .put("subtitle", subtitle.toString())
                        .put("chips", chips(templates, food, m.optString("shape", ""), m.optString("color", "")))
                        .put("spoken", sentence(templates, personName, title, food))
                        .put("shape", m.optString("shape", "ROUND"))
                        .put("color", m.optString("color", "WHITE"));
                String photo = photos.get(m.optString("id"));
                if (photo != null) {
                    a.put("photoUri", photo);
                }
                alarms.put(a);
            }
        }
        return alarms;
    }

    /** "{{person}}, ఇది {{voiceName}}. {{foodPhrase}}." with the blanks filled in. */
    static String sentence(JSONObject t, String person, String voiceName, String food) {
        String template = t.optString("sentence", "{{person}}, {{voiceName}}. {{foodPhrase}}.");
        String phrase = obj(t, "foodPhrase").optString(food, "");
        return template.replace("{{person}}", person)
                .replace("{{voiceName}}", voiceName)
                .replace("{{foodPhrase}}", phrase);
    }

    static JSONArray chips(JSONObject t, String food, String shape, String colour) throws JSONException {
        JSONArray chips = new JSONArray();
        if (!"ANY".equals(food)) {
            chips.put(new JSONObject().put("text", obj(t, "food").optString(food, food))
                    .put("tone", "BEFORE".equals(food) ? "before" : "after"));
        }
        String look = join(obj(t, "shape").optString(shape, ""), obj(t, "colour").optString(colour, ""));
        if (!look.isEmpty()) {
            chips.put(new JSONObject().put("text", look).put("tone", "grey"));
        }
        return chips;
    }

    /** ["MONDAY", "SUNDAY"] → [1, 7] */
    static JSONArray isoDays(JSONArray names) {
        JSONArray out = new JSONArray();
        for (int i = 0; names != null && i < names.length(); i++) {
            for (int d = 0; d < DAYS.length; d++) {
                if (DAYS[d].equals(names.optString(i))) {
                    out.put(d + 1);
                }
            }
        }
        return out;
    }

    private static JSONObject obj(JSONObject t, String key) {
        JSONObject o = t.optJSONObject(key);
        return o != null ? o : new JSONObject();
    }

    private static String join(String a, String b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + " · " + b;
    }
}
