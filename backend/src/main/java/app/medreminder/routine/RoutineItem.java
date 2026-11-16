package app.medreminder.routine;

import app.medreminder.medicine.Schedule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

/** A calm daily reminder ("drink a glass of water"). Rings like a medicine alarm but softer; nothing is logged. */
@Entity
@Table(name = "routine_item")
public class RoutineItem {
    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "time_of_day", nullable = false)
    private LocalTime time;

    @Column(name = "days_of_week", nullable = false)
    private short daysMask;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "spoken_text", length = 200)
    private String spokenText;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RoutineItem() {
    }

    public RoutineItem(UUID familyId, UUID personId) {
        this.id = UUID.randomUUID();
        this.familyId = familyId;
        this.personId = personId;
        this.createdAt = Instant.now();
    }

    public void update(LocalTime time, Set<DayOfWeek> days, String label, String spokenText, boolean active) {
        this.time = time.truncatedTo(ChronoUnit.MINUTES);
        this.daysMask = Schedule.toMask(days);
        this.label = label;
        this.spokenText = spokenText;
        this.active = active;
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public UUID getPersonId() { return personId; }
    public LocalTime getTime() { return time; }
    public Set<DayOfWeek> getDays() { return Schedule.fromMask(daysMask); }
    public String getLabel() { return label; }
    public String getSpokenText() { return spokenText; }
    public boolean isActive() { return active; }
}
