package app.medreminder.family;

import app.medreminder.common.ApiException;
import app.medreminder.family.FamilyDtos.FamilyResponse;
import app.medreminder.family.FamilyDtos.MemberResponse;
import app.medreminder.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FamilyService {
    private final FamilyRepository families;
    private final FamilyMemberRepository members;
    private final FamilyAccess access;

    public FamilyService(FamilyRepository families, FamilyMemberRepository members, FamilyAccess access) {
        this.families = families;
        this.members = members;
        this.access = access;
    }

    /** Creates a family; the creator becomes its first editor. */
    @Transactional
    public FamilyResponse create(String name) {
        UUID userId = CurrentUser.get().userId();
        ensureNotInFamily(userId);
        Family f = families.save(new Family(name.trim()));
        FamilyMember m = members.save(new FamilyMember(f.getId(), userId, Role.EDITOR));
        return toResponse(f, m);
    }

    /** Adds the caller to a family (used when redeeming a member invite code). */
    @Transactional
    public FamilyResponse join(UUID familyId, Role role) {
        UUID userId = CurrentUser.get().userId();
        ensureNotInFamily(userId);
        FamilyMember m = members.save(new FamilyMember(familyId, userId, role));
        return toResponse(load(familyId), m);
    }

    public void ensureNotInFamily(UUID userId) {
        if (members.existsByUserId(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "You are already in a family.");
        }
    }

    @Transactional(readOnly = true)
    public FamilyResponse mine() {
        FamilyMember m = access.member();
        return toResponse(load(m.getFamilyId()), m);
    }

    @Transactional
    public FamilyResponse rename(String name) {
        FamilyMember m = access.editor();
        Family f = load(m.getFamilyId());
        f.setName(name.trim());
        return toResponse(f, m);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers() {
        FamilyMember m = access.member();
        return members.listMembers(m.getFamilyId(), m.getUserId());
    }

    @Transactional
    public void changeRole(UUID memberId, Role role) {
        FamilyMember caller = access.editor();
        FamilyMember target = members.findByIdAndFamilyId(memberId, caller.getFamilyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Family member not found."));
        if (target.isEditor() && role != Role.EDITOR) {
            ensureAnotherEditor(caller.getFamilyId());
        }
        target.setRole(role);
    }

    /** Editors can remove anyone; anyone can remove themselves (leave). The last editor cannot leave. */
    @Transactional
    public void removeMember(UUID memberId) {
        FamilyMember caller = access.member();
        FamilyMember target = members.findByIdAndFamilyId(memberId, caller.getFamilyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Family member not found."));
        boolean self = target.getId().equals(caller.getId());
        if (!self && !caller.isEditor()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only family editors can change this.");
        }
        if (target.isEditor()) {
            ensureAnotherEditor(caller.getFamilyId());
        }
        members.delete(target);
    }

    private void ensureAnotherEditor(UUID familyId) {
        if (members.countByFamilyIdAndRole(familyId, Role.EDITOR) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "The family needs at least one editor. Make someone else an editor first.");
        }
    }

    public Family load(UUID familyId) {
        return families.findById(familyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Family not found."));
    }

    private static FamilyResponse toResponse(Family f, FamilyMember m) {
        return new FamilyResponse(f.getId(), f.getName(), m.getRole(), m.getId());
    }
}
