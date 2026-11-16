package app.medreminder.dose;

import app.medreminder.dose.DoseDtos.DaySummary;
import app.medreminder.dose.DoseDtos.DoseDetail;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Dose answers for family members (the Missed alert screen and Family Home). */
@RestController
public class DoseLogController {
    private final DoseLogService service;

    public DoseLogController(DoseLogService service) {
        this.service = service;
    }

    /** Missed doses from today and yesterday, for the banner on Family Home. */
    @GetMapping("/api/dose-logs/alerts")
    List<DoseDetail> alerts() {
        return service.alerts();
    }

    @GetMapping("/api/dose-logs/{id}")
    DoseDetail get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** "He already took it. Mark as taken" */
    @PostMapping("/api/dose-logs/{id}/taken")
    DoseDetail markTaken(@PathVariable UUID id) {
        return service.markTaken(id);
    }

    /** "Play the reminder again": wakes the person's phone and rings now. 503 if push is not set up. */
    @PostMapping("/api/dose-logs/{id}/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void replay(@PathVariable UUID id) {
        service.replay(id);
    }

    @GetMapping("/api/people/{personId}/week")
    List<DaySummary> week(@PathVariable UUID personId) {
        return service.week(personId);
    }
}
