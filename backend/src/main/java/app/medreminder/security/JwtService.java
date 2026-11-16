package app.medreminder.security;

import app.medreminder.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {
    static final String CLAIM_TYPE = "typ";
    static final String TYPE_USER = "user";

    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.ttl = props.jwt().ttl() != null ? props.jwt().ttl() : Duration.ofDays(7);
        this.issuer = props.jwt().issuer();
    }

    public String issueUserToken(UUID userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("email", email)
                .claim(CLAIM_TYPE, TYPE_USER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Duration ttl() {
        return ttl;
    }

    /** Returns the principal if the token is valid, signed by us, not expired, and a user token. */
    public Optional<AuthPrincipal> parseUserToken(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                    .parseSignedClaims(token).getPayload();
            if (!TYPE_USER.equals(c.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(new AuthPrincipal(UUID.fromString(c.getSubject()), c.get("email", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
