package app.medreminder.common;

import app.medreminder.push.PushSender;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Liveness for the host and the keep-awake job. Touches no database, so a sleeping database can stay asleep. */
@RestController
public class HealthController {
    private final PushSender push;

    public HealthController(PushSender push) {
        this.push = push;
    }

    /** "push" says whether Firebase is set up, so a hosted setup can be checked without reading logs. */
    @GetMapping("/api/health")
    Map<String, String> health() {
        return Map.of("status", "ok", "push", push.enabled() ? "on" : "off");
    }
}
