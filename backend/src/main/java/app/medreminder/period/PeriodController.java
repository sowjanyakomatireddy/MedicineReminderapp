package app.medreminder.period;

import app.medreminder.period.PeriodService.PeriodResponse;
import app.medreminder.period.PeriodService.Settings;
import app.medreminder.period.PeriodService.StartRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Period dates. Estimates only, not medical advice; the app says so wherever dates are shown. */
@RestController
@RequestMapping("/api/people/{personId}/period")
public class PeriodController {
    private final PeriodService service;

    public PeriodController(PeriodService service) {
        this.service = service;
    }

    @GetMapping
    PeriodResponse get(@PathVariable UUID personId) {
        return service.get(personId);
    }

    /** Editors: on/off, cycle length, reminder days before and time. */
    @PutMapping
    PeriodResponse settings(@PathVariable UUID personId, @Valid @RequestBody Settings settings) {
        return service.saveSettings(personId, settings);
    }

    /** "Period started today" — body may be empty, or { "date": "2026-11-14" }. */
    @PostMapping("/starts")
    PeriodResponse start(@PathVariable UUID personId, @RequestBody(required = false) StartRequest req) {
        return service.recordStart(personId, req == null ? null : req.date());
    }

    @DeleteMapping("/starts/{date}")
    PeriodResponse deleteStart(@PathVariable UUID personId, @PathVariable LocalDate date) {
        return service.deleteStart(personId, date);
    }
}
