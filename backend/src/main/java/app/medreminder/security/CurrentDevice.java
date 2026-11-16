package app.medreminder.security;

import app.medreminder.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

/** Helper to read the calling phone inside device endpoints. */
public final class CurrentDevice {
    private CurrentDevice() {
    }

    public static DevicePrincipal get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof DevicePrincipal p) {
            return p;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Not a linked phone.");
    }
}
