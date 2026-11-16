package app.medreminder.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A family member's browser or phone that can receive web push alerts. */
@Entity
@Table(name = "user_push_token")
public class UserPushToken {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 1024)
    private String token;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected UserPushToken() {
    }

    public UserPushToken(UUID userId, String token) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.token = token;
        this.createdAt = Instant.now();
        this.lastSeenAt = createdAt;
    }

    public UUID getUserId() { return userId; }
    public String getToken() { return token; }

    /** The same browser may move to another account (e.g. a shared tablet). */
    public void seenBy(UUID userId) {
        this.userId = userId;
        this.lastSeenAt = Instant.now();
    }
}
