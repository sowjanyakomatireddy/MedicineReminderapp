package app.medreminder.security;

import java.util.UUID;

/** A paired phone, taken from a verified device token. */
public record DevicePrincipal(UUID deviceId) {
}
