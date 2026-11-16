package app.medreminder.today;

import app.medreminder.today.TodayService.PersonToday;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class TodayController {
    private final TodayService service;

    public TodayController(TodayService service) {
        this.service = service;
    }

    /** Today's doses for every person in the caller's family (each in their own time zone). */
    @GetMapping("/api/families/me/today")
    List<PersonToday> today() {
        return service.today(Instant.now());
    }
}
