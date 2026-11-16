package app.medreminder.photo;

import app.medreminder.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Stores photos as files named "<uuid>.<ext>" in PHOTO_DIR (default ./uploads, git-ignored). */
@Component
public class LocalDiskPhotoStorage implements PhotoStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalDiskPhotoStorage.class);
    /** Keys are only ever ones we made; anything else (e.g. "../x") is refused. */
    private static final Pattern KEY = Pattern.compile("^[0-9a-f-]{36}\\.(jpg|png|webp)$");
    private static final Map<String, String> EXT = Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");
    private static final Map<String, String> TYPE = Map.of("jpg", "image/jpeg", "png", "image/png", "webp", "image/webp");

    private final Path dir;

    public LocalDiskPhotoStorage(AppProperties props) {
        this.dir = Path.of(props.storage().dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create photo folder " + dir, e);
        }
    }

    @Override
    public String save(byte[] data, String contentType) {
        String ext = EXT.get(contentType);
        if (ext == null) {
            throw new IllegalArgumentException("Unsupported type");
        }
        String key = UUID.randomUUID() + "." + ext;
        try {
            Files.write(dir.resolve(key), data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return key;
    }

    @Override
    public Optional<Photo> load(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            return Optional.empty();
        }
        Path file = dir.resolve(key);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Photo(Files.readAllBytes(file), TYPE.get(key.substring(key.lastIndexOf('.') + 1))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(dir.resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete an old photo file: {}", e.getClass().getSimpleName());
        }
    }
}
