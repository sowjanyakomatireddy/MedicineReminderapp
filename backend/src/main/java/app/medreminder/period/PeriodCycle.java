package app.medreminder.period;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One recorded period start date. */
@Entity
@Table(name = "period_cycle")
public class PeriodCycle {
    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PeriodCycle() {
    }

    public PeriodCycle(UUID familyId, UUID personId, LocalDate startDate) {
        this.id = UUID.randomUUID();
        this.familyId = familyId;
        this.personId = personId;
        this.startDate = startDate;
        this.createdAt = Instant.now();
    }

    public LocalDate getStartDate() { return startDate; }
}
