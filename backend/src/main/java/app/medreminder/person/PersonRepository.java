package app.medreminder.person;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonRepository extends JpaRepository<Person, UUID> {
    List<Person> findByFamilyIdOrderByCreatedAt(UUID familyId);

    Optional<Person> findByIdAndFamilyId(UUID id, UUID familyId);
}
