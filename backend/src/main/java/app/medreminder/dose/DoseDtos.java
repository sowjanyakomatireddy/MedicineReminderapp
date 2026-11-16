package app.medreminder.dose;

import app.medreminder.medicine.FoodRule;
import app.medreminder.medicine.PillColor;
import app.medreminder.medicine.PillShape;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class DoseDtos {
    private DoseDtos() {
    }

    /**
     * One log from the phone's offline queue. `id` is made on the phone, so sending it again is harmless.
     * `at` is when it happened (epoch milliseconds). Unknown or non-medicine schedule ids are accepted and dropped.
     */
    public record PhoneLog(@NotBlank @Size(max = 64) String id, @NotBlank @Size(max = 64) String scheduleId,
                           @NotNull LocalDate date, @NotNull DoseLog.Status status, Long at) {
    }

    /** Ids the phone may now remove from its queue. */
    public record Accepted(List<String> accepted) {
    }

    /** Everything the Missed alert screen shows. */
    public record DoseDetail(UUID id, UUID personId, String personName, String personPhone, UUID medicineId,
                             String medicineName, String voiceName, PillShape shape, PillColor color, boolean hasPhoto,
                             String photoVersion, FoodRule food, LocalDate date,
                             @JsonFormat(pattern = "HH:mm") LocalTime time, DoseLog.Status status,
                             DoseLog.Source source, Instant loggedAt, Integer rings, boolean phoneLinked,
                             boolean pushEnabled) {
    }

    /** One day in the "This week" strip. */
    public record DaySummary(LocalDate date, int total, int taken, int missed) {
    }

    public record TokenRequest(@NotBlank @Size(max = 1024) String token) {
    }
}
