package app.medreminder.medicine;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MedicineDtos {
    private MedicineDtos() {
    }

    /**
     * One alarm time. Send `id` to update an existing time; leave it out to add one.
     * Times left out of a medicine update are deleted. Unset numbers use the defaults (5 / 5 / 30).
     */
    public record ScheduleRequest(
            UUID id,
            @NotNull LocalTime time,
            @NotEmpty Set<DayOfWeek> days,
            @NotNull FoodRule food,
            @Min(0) @Max(120) Integer remindBeforeMin,
            @Min(1) @Max(60) Integer repeatEveryMin,
            @Min(0) @Max(240) Integer stopAfterMin,
            Boolean active) {
    }

    /** personId is only used when creating; a medicine cannot move to another person. */
    public record MedicineRequest(
            UUID personId,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String voiceName,
            PillShape shape,
            PillColor color,
            @Size(max = 500) String notes,
            @NotEmpty @Size(max = 12) List<@Valid ScheduleRequest> schedules) {
    }

    public record ScheduleResponse(UUID id, @JsonFormat(pattern = "HH:mm") LocalTime time, Set<DayOfWeek> days, FoodRule food, int remindBeforeMin,
                                   int repeatEveryMin, int stopAfterMin, boolean active) {
        static ScheduleResponse of(Schedule s) {
            return new ScheduleResponse(s.getId(), s.getTime(), s.getDays(), s.getFood(), s.getRemindBeforeMin(),
                    s.getRepeatEveryMin(), s.getStopAfterMin(), s.isActive());
        }
    }

    /** photoVersion changes whenever the photo changes, so the app can refresh its cached image. */
    public record MedicineResponse(UUID id, UUID personId, String name, String voiceName, PillShape shape,
                                   PillColor color, String notes, boolean hasPhoto, String photoVersion,
                                   List<ScheduleResponse> schedules, Instant updatedAt) {
    }
}
