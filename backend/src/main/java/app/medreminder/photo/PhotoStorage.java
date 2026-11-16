package app.medreminder.photo;

import java.util.Optional;

/**
 * Where photos live. Local disk in development (LocalDiskPhotoStorage); a cloud bucket can implement
 * this later without touching the rest of the app.
 */
public interface PhotoStorage {
    record Photo(byte[] data, String contentType) {
    }

    /** Stores the bytes and returns a new random key. */
    String save(byte[] data, String contentType);

    Optional<Photo> load(String key);

    void delete(String key);
}
