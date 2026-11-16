package app.medreminder.alarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Map;

/** Server sync data + the person's language templates → alarms (what a SYNC push does with the app closed). */
public class AlarmBuilderTest {
    private static JSONObject telugu() throws Exception {
        return new JSONObject()
                .put("sentence", "{{person}}, ఇది {{voiceName}}. {{foodPhrase}}.")
                .put("foodPhrase", new JSONObject().put("BEFORE", "భోజనానికి ముందు వేసుకోండి")
                        .put("AFTER", "భోజనం తర్వాత వేసుకోండి").put("ANY", "ఇప్పుడు వేసుకోండి"))
                .put("food", new JSONObject().put("BEFORE", "భోజనానికి ముందు").put("AFTER", "భోజనం తర్వాత"))
                .put("shape", new JSONObject().put("ROUND", "గుండ్రని"))
                .put("colour", new JSONObject().put("WHITE", "తెల్లని"));
    }

    private static JSONObject sync() throws Exception {
        JSONObject morning = new JSONObject().put("id", "s1").put("time", "07:00")
                .put("days", new JSONArray().put("MONDAY").put("SUNDAY")).put("food", "BEFORE")
                .put("remindBeforeMin", 5).put("repeatEveryMin", 5).put("stopAfterMin", 30).put("active", true);
        JSONObject paused = new JSONObject(morning.toString()).put("id", "s2").put("active", false);
        JSONObject med = new JSONObject().put("id", "m1").put("name", "Amlodipine 5 mg").put("voiceName", "బీపీ మాత్ర")
                .put("shape", "ROUND").put("color", "WHITE").put("notes", "1 tablet").put("hasPhoto", true)
                .put("schedules", new JSONArray().put(morning).put(paused));
        return new JSONObject().put("person", new JSONObject().put("name", "తాతయ్య"))
                .put("medicines", new JSONArray().put(med));
    }

    @Test
    public void buildsOneAlarmPerActiveTimeInThePersonsLanguage() throws Exception {
        JSONArray alarms = AlarmBuilder.build(sync(), telugu(), Map.of("m1", "file:///photos/m1.img"));
        assertEquals(1, alarms.length()); // the paused time is left out
        JSONObject a = alarms.getJSONObject(0);
        assertEquals("s1", a.getString("id"));
        assertEquals("తాతయ్య, ఇది బీపీ మాత్ర. భోజనానికి ముందు వేసుకోండి.", a.getString("spoken"));
        assertEquals("బీపీ మాత్ర", a.getString("title"));
        assertEquals("Amlodipine 5 mg · 1 tablet", a.getString("subtitle"));
        assertEquals("[1,7]", a.getJSONArray("days").toString());
        assertEquals("భోజనానికి ముందు", a.getJSONArray("chips").getJSONObject(0).getString("text"));
        assertEquals("before", a.getJSONArray("chips").getJSONObject(0).getString("tone"));
        assertEquals("గుండ్రని · తెల్లని", a.getJSONArray("chips").getJSONObject(1).getString("text"));
        assertEquals("file:///photos/m1.img", a.getString("photoUri"));
    }

    @Test
    public void anyTimeHasNoFoodChipAndNoVoiceNameFallsBackToTheName() throws Exception {
        JSONObject s = sync();
        JSONObject med = s.getJSONArray("medicines").getJSONObject(0);
        med.remove("voiceName");
        med.getJSONArray("schedules").getJSONObject(0).put("food", "ANY");
        JSONObject a = AlarmBuilder.build(s, telugu(), Map.of()).getJSONObject(0);
        assertEquals("తాతయ్య, ఇది Amlodipine 5 mg. ఇప్పుడు వేసుకోండి.", a.getString("spoken"));
        assertEquals(1, a.getJSONArray("chips").length());
        assertFalse(a.has("photoUri"));
    }

    @Test
    public void routineAndPeriodRingCalmlyInThePersonsLanguage() throws Exception {
        JSONObject t = telugu()
                .put("routineSentence", "{{person}}, {{text}}.")
                .put("periodSentence", "{{person}}, పీరియడ్స్ తేదీ దగ్గరలో ఉంది.")
                .put("periodTitle", "పీరియడ్స్ తేదీ దగ్గరలో ఉంది");
        JSONObject water = new JSONObject().put("id", "r1").put("time", "06:30").put("label", "Drink water")
                .put("spokenText", "నీళ్ళు తాగండి").put("days", new JSONArray().put("MONDAY")).put("active", true);
        JSONObject walk = new JSONObject().put("id", "r2").put("time", "17:00").put("label", "Evening walk")
                .put("days", new JSONArray().put("SUNDAY")).put("active", true);
        JSONObject off = new JSONObject(walk.toString()).put("id", "r3").put("active", false);
        JSONObject s = new JSONObject().put("person", new JSONObject().put("name", "అమ్మ"))
                .put("medicines", new JSONArray())
                .put("routine", new JSONArray().put(water).put(walk).put(off))
                .put("period", new JSONObject().put("date", "2026-11-18").put("time", "09:00"));

        JSONArray alarms = AlarmBuilder.build(s, t, Map.of());
        assertEquals(3, alarms.length());
        JSONObject a = alarms.getJSONObject(0);
        assertEquals("ROUTINE", a.getString("kind"));
        assertEquals("అమ్మ, నీళ్ళు తాగండి.", a.getString("spoken"));
        assertEquals("Drink water", a.getString("subtitle"));
        assertEquals(10, a.getInt("stopAfterMin"));
        // No spoken text: the voice reads the label.
        assertEquals("అమ్మ, Evening walk.", alarms.getJSONObject(1).getString("spoken"));
        JSONObject p = alarms.getJSONObject(2);
        assertEquals("period", p.getString("id"));
        assertEquals("2026-11-18", p.getString("date"));
        assertEquals("అమ్మ, పీరియడ్స్ తేదీ దగ్గరలో ఉంది.", p.getString("spoken"));
    }
}
