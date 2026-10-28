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
    /** Too many failed logins from this address; see the Retry-After header. */
    LOGIN_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
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
    /** A paging cursor is malformed or does not belong to this cluster. */
    INVALID_CURSOR(HttpStatus.BAD_REQUEST),
    /** The SQL predicate cannot be parsed or evaluated. */
    INVALID_PREDICATE(HttpStatus.BAD_REQUEST),
    /** A destructive operation was sent without the required confirmation. */
    CONFIRMATION_REQUIRED(HttpStatus.BAD_REQUEST),
    /** The requested structure type is not supported by this endpoint. */
    UNSUPPORTED_STRUCTURE_TYPE(HttpStatus.BAD_REQUEST),

    MAP_NOT_FOUND(HttpStatus.NOT_FOUND),
    STRUCTURE_NOT_FOUND(HttpStatus.NOT_FOUND),
    ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    /** The admin client is not connected to the cluster. */
    CLUSTER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    /** The cluster did not answer in time. */
    CLUSTER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),

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
