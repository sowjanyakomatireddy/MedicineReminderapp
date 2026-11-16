package app.medreminder.auth;

import app.medreminder.auth.AuthDtos.AuthResponse;
import app.medreminder.auth.AuthDtos.LoginRequest;
import app.medreminder.auth.AuthDtos.RegisterRequest;
import app.medreminder.auth.AuthDtos.UserResponse;
import app.medreminder.common.ApiException;
import app.medreminder.security.JwtService;
import app.medreminder.user.AppUser;
import app.medreminder.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        String lang = req.appLanguage() == null ? "en" : req.appLanguage();
        AppUser u = users.save(new AppUser(email, encoder.encode(req.password()), req.displayName().trim(), lang));
        return toAuth(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        AppUser u = users.findByEmail(normalize(req.email()))
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is wrong."));
        return toAuth(u);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return users.findById(userId).map(AuthService::toUser)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found."));
    }

    private AuthResponse toAuth(AppUser u) {
        return new AuthResponse(jwt.issueUserToken(u.getId(), u.getEmail()), jwt.ttl().toSeconds(), toUser(u));
    }

    static UserResponse toUser(AppUser u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getAppLanguage());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
