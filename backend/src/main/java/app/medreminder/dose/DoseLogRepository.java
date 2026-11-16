package app.medreminder.dose;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoseLogRepository extends JpaRepository<DoseLog, UUID> {
    Optional<DoseLog> findByScheduleIdAndDoseDate(UUID scheduleId, LocalDate doseDate);

    boolean existsByScheduleIdAndDoseDate(UUID scheduleId, LocalDate doseDate);

    Optional<DoseLog> findByIdAndFamilyId(UUID id, UUID familyId);

    List<DoseLog> findByPersonIdAndDoseDateBetween(UUID personId, LocalDate from, LocalDate to);

    List<DoseLog> findByPersonIdInAndDoseDateBetween(Collection<UUID> personIds, LocalDate from, LocalDate to);

    List<DoseLog> findByFamilyIdAndStatusAndDoseDateGreaterThanEqualOrderByDoseDateDescDoseTimeDesc(
            UUID familyId, DoseLog.Status status, LocalDate from);
}
