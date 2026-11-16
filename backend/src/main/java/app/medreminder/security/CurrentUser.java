package app.medreminder.security;

import app.medreminder.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

/** Helper to read the logged-in caller inside services/controllers. */
public final class CurrentUser {
    private CurrentUser() {
    }

    public static AuthPrincipal get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
            return p;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Not logged in.");
    }
}
