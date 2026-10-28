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

    /** A key or value is missing although the selected type requires one. */
    VALUE_REQUIRED(HttpStatus.BAD_REQUEST),
    /** The input cannot be converted to the selected key/value type. */
    VALUE_CONVERSION_FAILED(HttpStatus.BAD_REQUEST),
    /** The input is not well-formed JSON. */
    INVALID_JSON(HttpStatus.BAD_REQUEST),
    /** The class requested for a {@code CLASS} value is not on the admin (DTO) classpath. */
    CLASS_NOT_FOUND(HttpStatus.BAD_REQUEST),
    /** The class requested for a {@code CLASS} value belongs to a denied package. */
    CLASS_NOT_ALLOWED(HttpStatus.BAD_REQUEST),
    /** JSON could not be bound to the requested class. */
    CLASS_INSTANTIATION_FAILED(HttpStatus.BAD_REQUEST),
    /** A {@code COMPACT} value was submitted without its schema. */
    SCHEMA_REQUIRED(HttpStatus.BAD_REQUEST),
    /** A {@code COMPACT} value does not match the submitted schema. */
    SCHEMA_INVALID(HttpStatus.BAD_REQUEST),
    /** A {@code keyRef} is not a valid serialized key. */
    INVALID_KEY_REF(HttpStatus.BAD_REQUEST),

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
