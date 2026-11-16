package app.medreminder.family;

import app.medreminder.family.FamilyDtos.FamilyRequest;
import app.medreminder.family.FamilyDtos.FamilyResponse;
import app.medreminder.family.FamilyDtos.MemberResponse;
import app.medreminder.family.FamilyDtos.UpdateMemberRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The caller's family. "me" means "the family I belong to"; each user is in at most one family. */
@RestController
@RequestMapping("/api/families")
public class FamilyController {
    private final FamilyService service;

    public FamilyController(FamilyService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    FamilyResponse create(@Valid @RequestBody FamilyRequest req) {
        return service.create(req.name());
    }

    @GetMapping("/me")
    FamilyResponse mine() {
        return service.mine();
    }

    @PatchMapping("/me")
    FamilyResponse rename(@Valid @RequestBody FamilyRequest req) {
        return service.rename(req.name());
    }

    @GetMapping("/me/members")
    List<MemberResponse> members() {
        return service.listMembers();
    }

    @PatchMapping("/me/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeRole(@PathVariable UUID memberId, @Valid @RequestBody UpdateMemberRequest req) {
        service.changeRole(memberId, req.role());
    }

    @DeleteMapping("/me/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID memberId) {
        service.removeMember(memberId);
    }
}
