package app.medreminder.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A family member who logs in with email + password. */
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /** UI language of the family app (e.g. "en", "te"). Separate from any person's voice language. */
    @Column(name = "app_language", nullable = false, length = 10)
    private String appLanguage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, String displayName, String appLanguage) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.appLanguage = appLanguage;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getAppLanguage() { return appLanguage; }
    public Instant getCreatedAt() { return createdAt; }

    public void setAppLanguage(String appLanguage) { this.appLanguage = appLanguage; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
