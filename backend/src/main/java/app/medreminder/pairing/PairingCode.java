package app.medreminder.pairing;

import app.medreminder.family.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A one-time code. Only the SHA-256 hash is stored; the plain code is shown once, when created. */
@Entity
@Table(name = "pairing_code")
public class PairingCode {
    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PairingPurpose purpose;

    /** Role given to a family member who joins with a MEMBER code. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Role role;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected PairingCode() {
    }

    public PairingCode(UUID familyId, String codeHash, PairingPurpose purpose, Role role, UUID createdBy,
                       Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.familyId = familyId;
        this.codeHash = codeHash;
        this.purpose = purpose;
        this.role = role;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public PairingPurpose getPurpose() { return purpose; }
    public Role getRole() { return role; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }

    /** For tests only. */
    void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
