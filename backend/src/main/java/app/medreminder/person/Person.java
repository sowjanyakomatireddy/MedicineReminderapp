package app.medreminder.person;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Someone the family reminds (e.g. the grandfather). People do not log in; their phone is paired. */
@Entity
@Table(name = "person")
public class Person {
    public static final String DEFAULT_TIME_ZONE = "Asia/Kolkata";

    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(nullable = false, length = 60)
    private String name;

    /** Language the alarm speaks, e.g. "te". The app maps it to a TTS locale such as te-IN. */
    @Column(name = "voice_language", nullable = false, length = 10)
    private String voiceLanguage;

    @Column(name = "time_zone", nullable = false, length = 40)
    private String timeZone;

    @Column(name = "photo_key", length = 100)
    private String photoKey;

    /** The person's own phone number, for the family's "Call" button. */
    @Column(length = 20)
    private String phone;

    /** The number the person's phone calls with "Call family". */
    @Column(name = "family_phone", length = 20)
    private String familyPhone;

    /** Period reminders are off unless the family turns them on. */
    @Column(name = "period_tracking", nullable = false)
    private boolean periodTracking;

    /** Cycle length used until enough start dates are recorded to work out an average. */
    @Column(name = "cycle_length_days", nullable = false)
    private short cycleLengthDays = 28;

    @Column(name = "period_length_days", nullable = false)
    private short periodLengthDays = 5;

    /** The spoken reminder comes this many days before the expected date. */
    @Column(name = "period_remind_days", nullable = false)
    private short periodRemindDays = 2;

    @Column(name = "period_remind_time", nullable = false)
    private java.time.LocalTime periodRemindTime = java.time.LocalTime.of(9, 0);

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Person() {
    }

    public Person(UUID familyId, String name, String voiceLanguage, String timeZone) {
        this.id = UUID.randomUUID();
        this.familyId = familyId;
        this.name = name;
        this.voiceLanguage = voiceLanguage;
        this.timeZone = timeZone;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public String getName() { return name; }
    public String getVoiceLanguage() { return voiceLanguage; }
    public String getTimeZone() { return timeZone; }
    public Instant getCreatedAt() { return createdAt; }
    public String getPhotoKey() { return photoKey; }
    public String getPhone() { return phone; }
    public String getFamilyPhone() { return familyPhone; }
    public boolean isPeriodTracking() { return periodTracking; }
    public int getCycleLengthDays() { return cycleLengthDays; }
    public int getPeriodLengthDays() { return periodLengthDays; }
    public int getPeriodRemindDays() { return periodRemindDays; }
    public java.time.LocalTime getPeriodRemindTime() { return periodRemindTime; }

    public void setName(String name) { this.name = name; }
    public void setVoiceLanguage(String voiceLanguage) { this.voiceLanguage = voiceLanguage; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public void setPhotoKey(String photoKey) { this.photoKey = photoKey; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setFamilyPhone(String familyPhone) { this.familyPhone = familyPhone; }
    public void setPeriodTracking(boolean on) { this.periodTracking = on; }

    public void setPeriodSettings(int cycleLength, int periodLength, int remindDays, java.time.LocalTime remindTime) {
        this.cycleLengthDays = (short) cycleLength;
        this.periodLengthDays = (short) periodLength;
        this.periodRemindDays = (short) remindDays;
        this.periodRemindTime = remindTime.withSecond(0).withNano(0);
    }
}
