package app.medreminder.security;

import java.util.UUID;

/** The logged-in caller, taken from a verified JWT. */
public record AuthPrincipal(UUID userId, String email) {
}
