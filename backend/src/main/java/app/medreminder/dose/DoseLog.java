package app.medreminder.dose;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** The answer for one dose: TAKEN or MISSED, and who reported it. */
@Entity
@Table(name = "dose_log")
public class DoseLog {
    public enum Status { TAKEN, MISSED }

    /** PHONE: the elder's phone. FAMILY: "Mark as taken" in the family app. SERVER: the missed-dose job. */
    public enum Source { PHONE, FAMILY, SERVER }

    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Column(name = "medicine_id")
    private UUID medicineId;

    @Column(name = "medicine_name", nullable = false, length = 100)
    private String medicineName;

    @Column(name = "dose_date", nullable = false)
    private LocalDate doseDate;

    @Column(name = "dose_time", nullable = false)
    private LocalTime doseTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Source source;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DoseLog() {
    }

    public DoseLog(UUID id, UUID familyId, UUID personId, UUID scheduleId, UUID medicineId, String medicineName,
                   LocalDate doseDate, LocalTime doseTime, Status status, Source source, Instant loggedAt) {
        this.id = id;
        this.familyId = familyId;
        this.personId = personId;
        this.scheduleId = scheduleId;
        this.medicineId = medicineId;
        this.medicineName = medicineName;
        this.doseDate = doseDate;
        this.doseTime = doseTime;
        this.status = status;
        this.source = source;
        this.loggedAt = loggedAt;
        this.createdAt = Instant.now();
    }

    /** A late "taken" (from the phone after it was marked missed, or from the family) replaces "missed". */
    public void markTaken(Source by, Instant at) {
        this.status = Status.TAKEN;
        this.source = by;
        this.loggedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public UUID getPersonId() { return personId; }
    public UUID getScheduleId() { return scheduleId; }
    public UUID getMedicineId() { return medicineId; }
    public String getMedicineName() { return medicineName; }
    public LocalDate getDoseDate() { return doseDate; }
    public LocalTime getDoseTime() { return doseTime; }
    public Status getStatus() { return status; }
    public Source getSource() { return source; }
    public Instant getLoggedAt() { return loggedAt; }
}
