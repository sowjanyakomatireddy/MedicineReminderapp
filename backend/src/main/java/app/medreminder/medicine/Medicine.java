package app.medreminder.medicine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "medicine")
public class Medicine {
    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    /** The real name, e.g. "Amlodipine 5 mg". */
    @Column(nullable = false, length = 100)
    private String name;

    /** What the voice says, in the person's language, e.g. "బీపీ మాత్ర". Falls back to name. */
    @Column(name = "voice_name", length = 100)
    private String voiceName;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PillShape shape;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PillColor color;

    @Column(length = 500)
    private String notes;

    @Column(name = "photo_key", length = 100)
    private String photoKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Medicine() {
    }

    public Medicine(UUID familyId, UUID personId) {
        this.id = UUID.randomUUID();
        this.familyId = familyId;
        this.personId = personId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(String name, String voiceName, PillShape shape, PillColor color, String notes) {
        this.name = name;
        this.voiceName = voiceName;
        this.shape = shape;
        this.color = color;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public UUID getPersonId() { return personId; }
    public String getName() { return name; }
    public String getVoiceName() { return voiceName; }
    public PillShape getShape() { return shape; }
    public PillColor getColor() { return color; }
    public String getNotes() { return notes; }
    public String getPhotoKey() { return photoKey; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** The name the voice speaks. */
    public String spokenName() { return voiceName != null ? voiceName : name; }

    public void setPhotoKey(String photoKey) {
        this.photoKey = photoKey;
        this.updatedAt = Instant.now();
    }
}
