package app.medreminder.routine;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

public final class RoutineDtos {
    private RoutineDtos() {
    }

    /**
     * label: what the family sees (any language). spokenText: what the voice says, in the person's language;
     * if empty, the voice reads the label.
     */
    public record RoutineRequest(@NotNull LocalTime time, @NotEmpty Set<DayOfWeek> days,
                                 @NotBlank @Size(max = 100) String label, @Size(max = 200) String spokenText,
                                 Boolean active) {
    }

    public record RoutineResponse(UUID id, UUID personId, @JsonFormat(pattern = "HH:mm") LocalTime time,
                                  Set<DayOfWeek> days, String label, String spokenText, boolean active) {
        static RoutineResponse of(RoutineItem r) {
            return new RoutineResponse(r.getId(), r.getPersonId(), r.getTime(), r.getDays(), r.getLabel(),
                    r.getSpokenText(), r.isActive());
        }
    }
}
