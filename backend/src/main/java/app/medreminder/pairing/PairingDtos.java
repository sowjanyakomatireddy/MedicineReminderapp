package app.medreminder.pairing;

import app.medreminder.family.Role;
import app.medreminder.person.PersonDtos.PersonResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PairingDtos {
    private PairingDtos() {
    }

    /** role is only used for MEMBER codes (defaults to VIEWER). */
    public record CreateCodeRequest(@NotNull PairingPurpose purpose, Role role) {
    }

    /** The plain code is returned only here, once. */
    public record CodeResponse(String code, PairingPurpose purpose, Role role, Instant expiresAt) {
    }

    public record CodeRequest(@NotBlank @Size(max = 20) String code) {
    }

    public record PersonChoice(UUID id, String name, String voiceLanguage) {
    }

    /** Shown on the phone before redeeming, so it can ask "Which person is this phone?". */
    public record LookupResponse(String familyName, PairingPurpose purpose, List<PersonChoice> people) {
    }

    public record RedeemRequest(
            @NotBlank @Size(max = 20) String code,
            @NotNull UUID personId,
            @Size(max = 80) String deviceName,
            @Pattern(regexp = "^(android|ios|web)$", message = "Use android, ios or web") String platform) {
    }

    /** deviceToken is long-lived; it stops working when an editor unlinks the phone. */
    public record RedeemResponse(String deviceToken, UUID deviceId, String familyName, PersonResponse person) {
    }
}
