package app.medreminder.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * All app settings. Secrets come from environment variables (see application.yml and .env.example).
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, RateLimit rateLimit, Storage storage, Push push, Jobs jobs) {

    /**
     * Firebase push. credentialsFile: path to the service-account JSON (FIREBASE_CREDENTIALS); empty = push off.
     * appUrl: the family app's address, used for links in alerts.
     */
    public record Push(String credentialsFile, @NotBlank String appUrl) {
    }

    /** Background jobs (the missed-dose check). Off in tests, which call the job directly. */
    public record Jobs(boolean enabled, @Min(1) int missedGraceMinutes) {
    }

    /** Folder for uploaded photos (local disk in development). */
    public record Storage(@NotBlank String dir) {
    }

    public record Jwt(
            @NotBlank @Size(min = 32, message = "JWT_SECRET must be at least 32 characters") String secret,
            Duration ttl,
            @NotBlank String issuer) {
    }

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    /** Simple per-IP limit for sensitive endpoints (auth, pairing). */
    public record RateLimit(@Min(1) int requestsPerMinute, @NotEmpty List<String> paths) {
    }
}
