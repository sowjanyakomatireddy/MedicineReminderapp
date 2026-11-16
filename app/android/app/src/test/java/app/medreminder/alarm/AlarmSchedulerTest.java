package app.medreminder.alarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** The date logic of the alarm, run on the computer (no phone needed): ./gradlew testDebugUnitTest */
public class AlarmSchedulerTest {
    private static final TimeZone IST = TimeZone.getTimeZone("Asia/Kolkata");

    /** Monday 17 November 2025 at hh:mm in India. */
    private static long monday(int hour, int minute) {
        Calendar c = Calendar.getInstance(IST);
        c.clear();
        c.set(2025, Calendar.NOVEMBER, 17, hour, minute, 0);
        return c.getTimeInMillis();
    }

    private static JSONObject alarm(String time, int... days) throws Exception {
        JSONArray d = new JSONArray();
        for (int x : days) d.put(x);
        return new JSONObject().put("id", "s1").put("time", time).put("days", d)
                .put("remindBeforeMin", 5).put("repeatEveryMin", 5).put("stopAfterMin", 30);
    }

    @Test
    public void dailyAlarmHasAWindowFromFiveMinutesBeforeToThirtyAfter() throws Exception {
        List<AlarmScheduler.Occurrence> list =
                AlarmScheduler.occurrences(alarm("07:00", 1, 2, 3, 4, 5, 6, 7), IST, monday(6, 0));
        AlarmScheduler.Occurrence today = list.get(1); // [0] is yesterday
        assertEquals("s1@2025-11-17", today.key);
        assertEquals(monday(7, 0), today.doseAt);
        assertEquals(monday(6, 55), today.start);
        assertEquals(monday(7, 30), today.end);
    }

    @Test
    public void onlyChosenWeekdays() throws Exception {
        // Monday = 1, Wednesday = 3; from Sunday 16th to Tuesday 25th.
        List<AlarmScheduler.Occurrence> list =
                AlarmScheduler.occurrences(alarm("21:00", 1, 3), IST, monday(12, 0));
        for (AlarmScheduler.Occurrence o : list) {
            Calendar c = Calendar.getInstance(IST);
            c.setTimeInMillis(o.doseAt);
            int dow = c.get(Calendar.DAY_OF_WEEK);
            assertTrue(o.key, dow == Calendar.MONDAY || dow == Calendar.WEDNESDAY);
        }
        assertEquals("s1@2025-11-17", list.get(0).key);
        assertEquals("s1@2025-11-19", list.get(1).key);
    }

    @Test
    public void earlyMorningDoseStartsTheEveningBefore() throws Exception {
        // 00:02 with a 5-minute warning first rings at 23:57 the previous evening.
        JSONObject a = alarm("00:02", 2).put("remindBeforeMin", 5); // Tuesday
        AlarmScheduler.Occurrence o = AlarmScheduler.occurrences(a, IST, monday(23, 0)).get(0);
        assertEquals("s1@2025-11-18", o.key);
        assertEquals(monday(23, 57), o.start);
    }

    @Test
    public void oneOffTestAlarmUsesItsExactTime() throws Exception {
        long at = monday(10, 0);
        JSONObject a = new JSONObject().put("id", "test").put("at", at)
                .put("remindBeforeMin", 0).put("repeatEveryMin", 1).put("stopAfterMin", 3);
        List<AlarmScheduler.Occurrence> list = AlarmScheduler.occurrences(a, IST, at - 10_000);
        assertEquals(1, list.size());
        assertEquals(at, list.get(0).start);
        assertEquals(at + 3 * 60_000, list.get(0).end);
    }

    @Test
    public void dateUsesThePersonsTimeZoneNotThePhones() throws Exception {
        // 20:00 UTC Sunday is 01:30 Monday in India: the dose date is Monday's.
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(2025, Calendar.NOVEMBER, 16, 20, 0, 0);
        List<AlarmScheduler.Occurrence> list =
                AlarmScheduler.occurrences(alarm("07:00", 1), IST, utc.getTimeInMillis());
        assertEquals("s1@2025-11-17", list.get(0).key);
    }

    @Test
    public void oneOffOnADateRingsOnlyThatDay() throws Exception {
        JSONObject a = new JSONObject().put("id", "period").put("date", "2026-11-18").put("time", "09:00")
                .put("remindBeforeMin", 0).put("repeatEveryMin", 5).put("stopAfterMin", 10);
        List<AlarmScheduler.Occurrence> list = AlarmScheduler.occurrences(a, IST, monday(12, 0));
        assertEquals(1, list.size());
        assertEquals("period@2026-11-18", list.get(0).key);
        Calendar c = Calendar.getInstance(IST);
        c.clear();
        c.set(2026, Calendar.NOVEMBER, 18, 9, 0, 0);
        assertEquals(c.getTimeInMillis(), list.get(0).start);
    }
}
