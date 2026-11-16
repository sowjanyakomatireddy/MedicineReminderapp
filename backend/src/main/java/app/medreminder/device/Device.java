package app.medreminder.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A phone linked to a person with a pairing code. It calls the API with its own device token. */
@Entity
@Table(name = "device")
public class Device {
    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(length = 80)
    private String name;

    @Column(nullable = false, length = 20)
    private String platform;

    /** Firebase Cloud Messaging token, set by the phone in Phase 5. */
    @Column(name = "fcm_token", length = 4096)
    private String fcmToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    /** Set when an editor unlinks the phone; its token stops working. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Device() {
    }

    public Device(UUID familyId, UUID personId, String name, String platform) {
        this.id = UUID.randomUUID();
        this.familyId = familyId;
        this.personId = personId;
        this.name = name;
        this.platform = platform;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public UUID getPersonId() { return personId; }
    public String getName() { return name; }
    public String getPlatform() { return platform; }
    public String getFcmToken() { return fcmToken; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSyncAt() { return lastSyncAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public void revoke() { this.revokedAt = Instant.now(); }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }
    public void markSynced() { this.lastSyncAt = Instant.now(); }
}
