package io.github.noproblem2000.tsarevnacache.api.error;

import org.springframework.http.HttpStatus;

/**
 * Machine readable error codes returned by the API.
 *
 * <p>The frontend maps every code to the translation key {@code errors.<CODE>}, so new codes must
 * be added to both locale files.
 */
public enum ErrorCode {

    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    /** Default HTTP status for this code. */
    public HttpStatus status() {
        return status;
    }
}
