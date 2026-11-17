package app.medreminder.photo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores photos in the "photo" table, keyed "<uuid>.<ext>". Photos are small (max 5 MB, usually far less),
 * and keeping them in the database means a host without a lasting disk never loses them.
 */
@Component
public class DatabasePhotoStorage implements PhotoStorage {
    /** Keys are only ever ones we made. */
    private static final Pattern KEY = Pattern.compile("^[0-9a-f-]{36}\\.(jpg|png|webp)$");
    private static final Map<String, String> EXT = Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private final JdbcTemplate jdbc;

    public DatabasePhotoStorage(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String save(byte[] data, String contentType) {
        String ext = EXT.get(contentType);
        if (ext == null) {
            throw new IllegalArgumentException("Unsupported type");
        }
        String key = UUID.randomUUID() + "." + ext;
        jdbc.update("INSERT INTO photo (photo_key, content_type, data, created_at) VALUES (?, ?, ?, ?)",
                key, contentType, data, OffsetDateTime.now(ZoneOffset.UTC));
        return key;
    }

    @Override
    public Optional<Photo> load(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            return Optional.empty();
        }
        return jdbc.query("SELECT data, content_type FROM photo WHERE photo_key = ?",
                (rs, i) -> new Photo(rs.getBytes(1), rs.getString(2)), key).stream().findFirst();
    }

    @Override
    public void delete(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            return;
        }
        jdbc.update("DELETE FROM photo WHERE photo_key = ?", key);
    }
}
