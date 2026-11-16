package app.medreminder.family;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class FamilyDtos {
    private FamilyDtos() {
    }

    public record FamilyRequest(@NotBlank @Size(max = 80) String name) {
    }

    /** The caller's family, with the caller's own role. */
    public record FamilyResponse(UUID id, String name, Role myRole, UUID myMemberId) {
    }

    public record MemberResponse(UUID id, UUID userId, String displayName, String email, Role role, boolean me) {
    }

    public record UpdateMemberRequest(@NotNull Role role) {
    }
}
