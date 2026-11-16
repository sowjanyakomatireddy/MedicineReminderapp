package app.medreminder.period;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple estimates from recorded start dates. Not medical advice: the app always says so next to the dates.
 */
public final class PeriodMath {
    /** Gaps outside this range are probably a missed or mistyped date, so they are left out of the average. */
    static final int MIN_CYCLE = 15;
    static final int MAX_CYCLE = 60;
    /** Average over the most recent cycles only. */
    static final int RECENT = 6;

    private PeriodMath() {
    }

    /** Average of the last few cycle lengths (in days); {@code fallback} until there are any. */
    public static int averageCycle(List<LocalDate> startsAscending, int fallback) {
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < startsAscending.size(); i++) {
            long gap = ChronoUnit.DAYS.between(startsAscending.get(i - 1), startsAscending.get(i));
            if (gap >= MIN_CYCLE && gap <= MAX_CYCLE) {
                gaps.add(gap);
            }
        }
        if (gaps.isEmpty()) {
            return fallback;
        }
        List<Long> recent = gaps.subList(Math.max(0, gaps.size() - RECENT), gaps.size());
        return (int) Math.round(recent.stream().mapToLong(Long::longValue).average().orElse(fallback));
    }

    /**
     * The next expected start: last start + average, moved on by whole cycles if that date has passed
     * (a start that was not recorded). While the expected days are going on, it stays on the current one.
     */
    public static LocalDate nextExpected(LocalDate lastStart, int averageCycle, int periodLength, LocalDate today) {
        if (lastStart == null) {
            return null;
        }
        LocalDate next = lastStart.plusDays(averageCycle);
        while (next.plusDays(Math.max(1, periodLength) - 1L).isBefore(today)) {
            next = next.plusDays(averageCycle);
        }
        return next;
    }
}
