package app.medreminder.common;

import org.springframework.http.HttpStatus;

/** Throw from services to return a clean error with a given HTTP status. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
