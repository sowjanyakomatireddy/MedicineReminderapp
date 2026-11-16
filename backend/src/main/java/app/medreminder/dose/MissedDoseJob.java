package app.medreminder.dose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Every minute: doses whose alarm ended without an answer become MISSED and the family is alerted.
 * Runs on one server; with several servers, add a lock (e.g. ShedLock) so it runs once.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class MissedDoseJob {
    private static final Logger log = LoggerFactory.getLogger(MissedDoseJob.class);
    private final DoseLogService doses;

    public MissedDoseJob(DoseLogService doses) {
        this.doses = doses;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void run() {
        int n = doses.markMissed(Instant.now());
        if (n > 0) {
            log.info("Marked {} dose(s) as missed", n); // counts only: no health details in logs
        }
    }
}
