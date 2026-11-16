package app.medreminder.security;

import app.medreminder.device.DeviceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads "Authorization: Bearer <jwt>" and sets the security context.
 * A user token gives ROLE_USER (family app). A device token gives ROLE_DEVICE (paired phone),
 * but only while the phone is still linked.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final DeviceRepository devices;

    public JwtAuthFilter(JwtService jwtService, DeviceRepository devices) {
        this.jwtService = jwtService;
        this.devices = devices;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            var user = jwtService.parseUserToken(token);
            if (user.isPresent()) {
                authenticate(user.get(), "ROLE_USER");
            } else {
                jwtService.parseDeviceToken(token)
                        .filter(d -> devices.existsByIdAndRevokedAtIsNull(d.deviceId()))
                        .ifPresent(d -> authenticate(d, "ROLE_DEVICE"));
            }
        }
        chain.doFilter(request, response);
    }

    private static void authenticate(Object principal, String role) {
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
