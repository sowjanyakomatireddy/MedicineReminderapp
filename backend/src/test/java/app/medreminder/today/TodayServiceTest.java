package app.medreminder.today;

import app.medreminder.medicine.FoodRule;
import app.medreminder.medicine.Medicine;
import app.medreminder.medicine.Schedule;
import app.medreminder.person.Person;
import app.medreminder.today.TodayService.PersonToday;
import app.medreminder.today.TodayService.Status;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TodayServiceTest {
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    final UUID family = UUID.randomUUID();
    final Person thatha = new Person(family, "Thatha", "te", "Asia/Kolkata");
    final Medicine bp = medicine("Amlodipine 5 mg");

    Medicine medicine(String name) {
        Medicine m = new Medicine(family, thatha.getId());
        m.update(name, null, null, null, null);
        return m;
    }

    Schedule schedule(Medicine m, String time, Set<DayOfWeek> days, boolean active) {
        Schedule s = new Schedule(m.getId());
        s.update(LocalTime.parse(time), days, FoodRule.BEFORE, 5, 5, 30, active);
        return s;
    }

    /** Monday 17 November 2025 at the given local time in India. */
    static Instant monday(String time) {
        return ZonedDateTime.of(2025, 11, 17, 0, 0, 0, 0, IST).with(LocalTime.parse(time)).toInstant();
    }

    PersonToday at(Instant now, Schedule... schedules) {
        return TodayService.forPerson(thatha, List.of(schedules), Map.of(bp.getId(), bp), now);
    }

    @Test
    void statusFollowsTheAlarmWindow() {
        Schedule s = schedule(bp, "07:00", EnumSet.allOf(DayOfWeek.class), true);
        assertThat(at(monday("06:54"), s).doses().get(0).status()).isEqualTo(Status.UPCOMING);
        assertThat(at(monday("06:55"), s).doses().get(0).status()).isEqualTo(Status.DUE); // first reminder
        assertThat(at(monday("07:29"), s).doses().get(0).status()).isEqualTo(Status.DUE);
        assertThat(at(monday("07:30"), s).doses().get(0).status()).isEqualTo(Status.UNCONFIRMED); // auto stop
    }

    @Test
    void onlyActiveTimesForTodaysWeekdayAreListedInTimeOrder() {
        Schedule night = schedule(bp, "21:00", EnumSet.allOf(DayOfWeek.class), true);
        Schedule morning = schedule(bp, "07:00", EnumSet.of(DayOfWeek.MONDAY), true);
        Schedule sundayOnly = schedule(bp, "08:00", EnumSet.of(DayOfWeek.SUNDAY), true);
        Schedule paused = schedule(bp, "09:00", EnumSet.allOf(DayOfWeek.class), false);

        PersonToday today = at(monday("12:00"), night, morning, sundayOnly, paused);
        assertThat(today.total()).isEqualTo(2);
        assertThat(today.doses()).extracting(TodayService.Dose::time)
                .containsExactly(LocalTime.of(7, 0), LocalTime.of(21, 0));
        assertThat(today.doses().get(0).alertAt()).isEqualTo(LocalTime.of(6, 55));
    }

    @Test
    void todayIsThePersonsDateNotTheServers() {
        // 20:00 UTC on Sunday is already 01:30 on Monday in India.
        Instant sundayEveningUtc = ZonedDateTime.of(2025, 11, 16, 20, 0, 0, 0, ZoneId.of("UTC")).toInstant();
        Schedule mondayOnly = schedule(bp, "07:00", EnumSet.of(DayOfWeek.MONDAY), true);
        PersonToday today = at(sundayEveningUtc, mondayOnly);
        assertThat(today.date().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(today.total()).isEqualTo(1);
    }

    @Test
    void daysMaskRoundTrips() {
        Set<DayOfWeek> days = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY);
        assertThat(Schedule.toMask(days)).isEqualTo((short) (1 | 4 | 64));
        assertThat(Schedule.fromMask(Schedule.toMask(days))).isEqualTo(days);
    }
}
