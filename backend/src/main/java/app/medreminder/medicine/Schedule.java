package app.medreminder.medicine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * One alarm time for a medicine. The phone rings at (time - remindBeforeMin), repeats every repeatEveryMin,
 * and stops at (time + stopAfterMin), when the dose counts as missed if not taken.
 */
@Entity
@Table(name = "schedule")
public class Schedule {
    public static final int DEFAULT_REMIND_BEFORE = 5;
    public static final int DEFAULT_REPEAT_EVERY = 5;
    public static final int DEFAULT_STOP_AFTER = 30;

    @Id
    private UUID id;

    @Column(name = "medicine_id", nullable = false)
    private UUID medicineId;

    /** Local time in the person's time zone. */
    @Column(name = "time_of_day", nullable = false)
    private LocalTime time;

    /** Bit mask: Monday = 1 ... Sunday = 64. */
    @Column(name = "days_of_week", nullable = false)
    private short daysMask;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_rule", nullable = false, length = 6)
    private FoodRule food;

    @Column(name = "remind_before_min", nullable = false)
    private short remindBeforeMin;

    @Column(name = "repeat_every_min", nullable = false)
    private short repeatEveryMin;

    @Column(name = "stop_after_min", nullable = false)
    private short stopAfterMin;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Schedule() {
    }

    public Schedule(UUID medicineId) {
        this.id = UUID.randomUUID();
        this.medicineId = medicineId;
        this.createdAt = Instant.now();
    }

    public void update(LocalTime time, Set<DayOfWeek> days, FoodRule food, int remindBefore, int repeatEvery,
                       int stopAfter, boolean active) {
        this.time = time.truncatedTo(ChronoUnit.MINUTES);
        this.daysMask = toMask(days);
        this.food = food;
        this.remindBeforeMin = (short) remindBefore;
        this.repeatEveryMin = (short) repeatEvery;
        this.stopAfterMin = (short) stopAfter;
        this.active = active;
    }

    public static short toMask(Set<DayOfWeek> days) {
        int m = 0;
        for (DayOfWeek d : days) {
            m |= 1 << (d.getValue() - 1);
        }
        return (short) m;
    }

    public static Set<DayOfWeek> fromMask(int mask) {
        EnumSet<DayOfWeek> s = EnumSet.noneOf(DayOfWeek.class);
        Arrays.stream(DayOfWeek.values()).filter(d -> (mask & (1 << (d.getValue() - 1))) != 0).forEach(s::add);
        return s;
    }

    public boolean runsOn(DayOfWeek day) {
        return (daysMask & (1 << (day.getValue() - 1))) != 0;
    }

    public UUID getId() { return id; }
    public UUID getMedicineId() { return medicineId; }
    public LocalTime getTime() { return time; }
    public Set<DayOfWeek> getDays() { return fromMask(daysMask); }
    public FoodRule getFood() { return food; }
    public int getRemindBeforeMin() { return remindBeforeMin; }
    public int getRepeatEveryMin() { return repeatEveryMin; }
    public int getStopAfterMin() { return stopAfterMin; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
