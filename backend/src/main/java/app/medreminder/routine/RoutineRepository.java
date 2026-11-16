package app.medreminder.routine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoutineRepository extends JpaRepository<RoutineItem, UUID> {
    List<RoutineItem> findByPersonIdOrderByTimeAsc(UUID personId);

    Optional<RoutineItem> findByIdAndFamilyId(UUID id, UUID familyId);
}
