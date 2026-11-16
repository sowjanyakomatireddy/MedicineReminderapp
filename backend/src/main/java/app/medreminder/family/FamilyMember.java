package app.medreminder.family;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A logged-in user's place in a family, with a role. */
@Entity
@Table(name = "family_member")
public class FamilyMember {
    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FamilyMember() {
    }

    public FamilyMember(UUID familyId, UUID userId, Role role) {
        this.id = UUID.randomUUID();
        this.familyId = familyId;
        this.userId = userId;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public UUID getUserId() { return userId; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isEditor() { return role == Role.EDITOR; }

    public void setRole(Role role) { this.role = role; }
}
