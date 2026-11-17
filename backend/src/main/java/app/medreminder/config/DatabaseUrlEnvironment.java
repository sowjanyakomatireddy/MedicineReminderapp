package app.medreminder.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Hosted databases (Neon, Render, Railway...) hand out one line: postgresql://user:password@host/db?sslmode=require.
 * If DATABASE_URL is set, it is split into the JDBC url, user and password, so it can be pasted as it is.
 * DB_URL / DB_USER / DB_PASSWORD still work when DATABASE_URL is not set.
 */
public class DatabaseUrlEnvironment implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String url = env.getProperty("DATABASE_URL");
        if (url == null || url.isBlank()) {
            return;
        }
        env.getPropertySources().addFirst(new MapPropertySource("databaseUrl", parse(clean(url))));
    }

    /** Forgives what a copy from the host's page often carries: a "DATABASE_URL=" prefix and quotes. */
    static String clean(String raw) {
        String s = raw.trim().replaceFirst("^DATABASE_URL\\s*=\\s*", "").trim();
        if (s.length() >= 2 && (s.startsWith("\"") || s.startsWith("'")) && s.charAt(s.length() - 1) == s.charAt(0)) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    static Map<String, Object> parse(String url) {
        URI uri = URI.create(url.replaceFirst("^postgres(ql)?://", "http://"));
        if (!url.matches("^postgres(ql)?://.*") || uri.getHost() == null) {
            throw new IllegalStateException("DATABASE_URL must look like postgresql://user:password@host/database");
        }
        Map<String, Object> props = new HashMap<>();
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() > 0) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append(uri.getRawPath());
        if (uri.getRawQuery() != null) {
            jdbc.append('?').append(uri.getRawQuery());
        }
        props.put("spring.datasource.url", jdbc.toString());
        String info = uri.getRawUserInfo();
        if (info != null) {
            int colon = info.indexOf(':');
            props.put("spring.datasource.username", decode(colon < 0 ? info : info.substring(0, colon)));
            if (colon >= 0) {
                props.put("spring.datasource.password", decode(info.substring(colon + 1)));
            }
        }
        return props;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
