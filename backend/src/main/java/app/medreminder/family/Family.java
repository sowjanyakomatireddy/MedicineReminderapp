package app.medreminder.family;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "family")
public class Family {
    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Family() {
    }

    public Family(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
}
