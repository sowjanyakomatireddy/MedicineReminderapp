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
public record AppProperties(Jwt jwt, Cors cors, RateLimit rateLimit) {

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
