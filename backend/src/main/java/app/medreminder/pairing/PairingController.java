package app.medreminder.pairing;

import app.medreminder.family.FamilyDtos.FamilyResponse;
import app.medreminder.pairing.PairingDtos.CodeRequest;
import app.medreminder.pairing.PairingDtos.CodeResponse;
import app.medreminder.pairing.PairingDtos.CreateCodeRequest;
import app.medreminder.pairing.PairingDtos.LookupResponse;
import app.medreminder.pairing.PairingDtos.RedeemRequest;
import app.medreminder.pairing.PairingDtos.RedeemResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Family codes. Codes expire after 24 hours and work once. All /api/pairing endpoints are rate-limited per IP.
 * lookup and redeem need no login (the elder's phone has no account); codes and join need a family login.
 */
@RestController
@RequestMapping("/api/pairing")
public class PairingController {
    private final PairingService service;

    public PairingController(PairingService service) {
        this.service = service;
    }

    @PostMapping("/codes")
    @ResponseStatus(HttpStatus.CREATED)
    CodeResponse create(@Valid @RequestBody CreateCodeRequest req) {
        return service.create(req);
    }

    @PostMapping("/lookup")
    LookupResponse lookup(@Valid @RequestBody CodeRequest req) {
        return service.lookup(req.code());
    }

    @PostMapping("/redeem")
    @ResponseStatus(HttpStatus.CREATED)
    RedeemResponse redeem(@Valid @RequestBody RedeemRequest req) {
        return service.redeem(req);
    }

    @PostMapping("/join")
    FamilyResponse join(@Valid @RequestBody CodeRequest req) {
        return service.join(req.code());
    }
}
