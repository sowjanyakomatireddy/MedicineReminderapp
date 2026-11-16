package app.medreminder.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 100, message = "Password must be 8 to 100 characters") String password,
            @NotBlank @Size(max = 100) String displayName,
            @Pattern(regexp = "^[a-z]{2,3}$", message = "Use a language code like en, te, ta, hi") String appLanguage) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record UserResponse(UUID id, String email, String displayName, String appLanguage) {
    }

    public record AuthResponse(String token, long expiresInSeconds, UserResponse user) {
    }
}
