package app.medreminder.pairing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PairingCodeRepository extends JpaRepository<PairingCode, UUID> {
    Optional<PairingCode> findByCodeHash(String codeHash);

    /** Marks the code used only if nobody used it first. Returns 1 if this call won, 0 otherwise. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PairingCode c set c.usedAt = :now where c.id = :id and c.usedAt is null and c.expiresAt > :now")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);
}
