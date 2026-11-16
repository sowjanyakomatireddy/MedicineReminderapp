package app.medreminder.push;

import app.medreminder.dose.DoseDtos.TokenRequest;
import app.medreminder.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** A family member's browser registers here to get missed-dose alerts; removed on logout. */
@RestController
public class PushTokenController {
    private final UserPushTokenRepository tokens;

    public PushTokenController(UserPushTokenRepository tokens) {
        this.tokens = tokens;
    }

    @PostMapping("/api/me/push-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void register(@Valid @RequestBody TokenRequest req) {
        UUID me = CurrentUser.get().userId();
        tokens.findByToken(req.token()).ifPresentOrElse(t -> t.seenBy(me),
                () -> tokens.save(new UserPushToken(me, req.token())));
    }

    @DeleteMapping("/api/me/push-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void remove(@Valid @RequestBody TokenRequest req) {
        tokens.findByToken(req.token())
                .filter(t -> t.getUserId().equals(CurrentUser.get().userId()))
                .ifPresent(tokens::delete);
    }
}
