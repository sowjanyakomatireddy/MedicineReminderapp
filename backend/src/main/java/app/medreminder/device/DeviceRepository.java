package app.medreminder.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    long countByPersonIdAndRevokedAtIsNull(UUID personId);

    boolean existsByIdAndRevokedAtIsNull(UUID id);

    List<Device> findByFamilyIdAndRevokedAtIsNullOrderByCreatedAt(UUID familyId);

    List<Device> findByFamilyIdAndPersonIdAndRevokedAtIsNullOrderByCreatedAt(UUID familyId, UUID personId);

    Optional<Device> findByIdAndFamilyIdAndRevokedAtIsNull(UUID id, UUID familyId);

    Optional<Device> findByIdAndRevokedAtIsNull(UUID id);

    List<Device> findByPersonIdAndRevokedAtIsNullAndFcmTokenIsNotNull(UUID personId);
}
