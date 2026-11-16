package app.medreminder.photo;

import app.medreminder.common.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** Checks uploads are real JPEG/PNG/WebP images (by their first bytes, not the name) and serves them. */
@Service
public class PhotoService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private final PhotoStorage storage;

    public PhotoService(PhotoStorage storage) {
        this.storage = storage;
    }

    /** Saves a new photo and deletes the old one. Returns the new key. */
    public String replace(String oldKey, MultipartFile file) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read the photo.");
        }
        if (data.length == 0 || data.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The photo must be smaller than 5 MB.");
        }
        String type = detect(data);
        if (type == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use a JPEG, PNG or WebP photo.");
        }
        String key = storage.save(data, type);
        storage.delete(oldKey);
        return key;
    }

    public void delete(String key) {
        storage.delete(key);
    }

    public ResponseEntity<byte[]> serve(String key) {
        PhotoStorage.Photo p = storage.load(key)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No photo."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.contentType()))
                // Keys change on every upload, so the browser may keep a copy; "private" keeps it off shared caches.
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(p.data());
    }

    /** Short version string so the app can bust its image cache when the photo changes. */
    public static String version(String key) {
        return key == null ? null : key.substring(0, 8);
    }

    static String detect(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8 && Arrays.equals(Arrays.copyOf(d, 8),
                new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})) {
            return "image/png";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
