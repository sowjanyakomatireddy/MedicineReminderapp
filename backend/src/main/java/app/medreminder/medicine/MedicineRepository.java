package app.medreminder.medicine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicineRepository extends JpaRepository<Medicine, UUID> {
    List<Medicine> findByFamilyIdOrderByCreatedAt(UUID familyId);

    List<Medicine> findByFamilyIdAndPersonIdOrderByCreatedAt(UUID familyId, UUID personId);

    Optional<Medicine> findByIdAndFamilyId(UUID id, UUID familyId);

    long countByPersonId(UUID personId);
}
