package app.medreminder.family;

import app.medreminder.common.ApiException;
import app.medreminder.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Finds the caller's family membership. Every family-scoped query starts here,
 * so a user can never read or change another family's data.
 */
@Component
public class FamilyAccess {
    private final FamilyMemberRepository members;

    public FamilyAccess(FamilyMemberRepository members) {
        this.members = members;
    }

    /** The caller's membership, or 404 if they have not created or joined a family yet. */
    public FamilyMember member() {
        return members.findByUserId(CurrentUser.get().userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "You are not in a family yet."));
    }

    /** The caller's membership, or 403 if they are only a viewer. */
    public FamilyMember editor() {
        FamilyMember m = member();
        if (!m.isEditor()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only family editors can change this.");
        }
        return m;
    }
}
