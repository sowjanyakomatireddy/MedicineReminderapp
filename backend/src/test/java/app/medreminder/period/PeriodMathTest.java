package app.medreminder.period;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodMathTest {
    static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    @Test
    void averageUsesTheSettingUntilThereAreTwoDates() {
        assertThat(PeriodMath.averageCycle(List.of(), 28)).isEqualTo(28);
        assertThat(PeriodMath.averageCycle(List.of(d("2026-10-01")), 30)).isEqualTo(30);
    }

    @Test
    void averageOfRecentCyclesIgnoringOddGaps() {
        // 30, 28, (a missed month: 58 is within range but 70 is not), 29
        List<LocalDate> starts = List.of(d("2026-01-01"), d("2026-01-31"), d("2026-02-28"),
                d("2026-05-09"), d("2026-06-07"));
        // gaps: 30, 28, 70 (left out), 29 → average 29
        assertThat(PeriodMath.averageCycle(starts, 28)).isEqualTo(29);
    }

    @Test
    void onlyTheLastSixCyclesCount() {
        LocalDate start = d("2025-01-01");
        List<LocalDate> starts = new java.util.ArrayList<>(List.of(start));
        int[] gaps = {40, 40, 40, 28, 28, 28, 28, 28, 28};
        for (int g : gaps) {
            starts.add(starts.get(starts.size() - 1).plusDays(g));
        }
        assertThat(PeriodMath.averageCycle(starts, 30)).isEqualTo(28);
    }

    @Test
    void nextExpectedRollsOnIfAStartWasNotRecorded() {
        LocalDate last = d("2026-10-01");
        assertThat(PeriodMath.nextExpected(last, 28, 5, d("2026-10-10"))).isEqualTo(d("2026-10-29"));
        // During the expected days it stays on the current date...
        assertThat(PeriodMath.nextExpected(last, 28, 5, d("2026-11-02"))).isEqualTo(d("2026-10-29"));
        // ...and after them, moves on to the next cycle.
        assertThat(PeriodMath.nextExpected(last, 28, 5, d("2026-11-03"))).isEqualTo(d("2026-11-26"));
        assertThat(PeriodMath.nextExpected(null, 28, 5, d("2026-11-03"))).isNull();
    }
}
