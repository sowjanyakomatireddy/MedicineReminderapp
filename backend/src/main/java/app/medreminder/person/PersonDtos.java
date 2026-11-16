package app.medreminder.person;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class PersonDtos {
    private PersonDtos() {
    }

    /**
     * voiceLanguage is a short language code (en, hi, te, ta, kn, ...). The supported list lives in the app
     * (app/src/i18n/locales.ts), so adding a language does not need a backend change.
     */
    public record PersonRequest(
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Pattern(regexp = "^[a-z]{2,3}$", message = "Use a language code like te, ta, hi, en") String voiceLanguage,
            @Size(max = 40) String timeZone,
            @Pattern(regexp = PHONE, message = "Use digits, spaces, + ( ) or -") String phone,
            @Pattern(regexp = PHONE, message = "Use digits, spaces, + ( ) or -") String familyPhone) {
    }

    /** Empty, or 6 to 20 of digits, spaces, + ( ) -. */
    static final String PHONE = "^$|^[+0-9 ()\\-]{6,20}$";

    public record PersonResponse(UUID id, String name, String voiceLanguage, String timeZone, long linkedPhones,
                                 long medicineCount, boolean hasPhoto, String photoVersion, String phone,
                                 String familyPhone) {
    }
}
