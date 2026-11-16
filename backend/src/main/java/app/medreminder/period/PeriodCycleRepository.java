package app.medreminder.period;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PeriodCycleRepository extends JpaRepository<PeriodCycle, UUID> {
    List<PeriodCycle> findByPersonIdOrderByStartDateAsc(UUID personId);

    Optional<PeriodCycle> findByPersonIdAndStartDate(UUID personId, LocalDate startDate);
}
