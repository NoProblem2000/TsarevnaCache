package io.github.noproblem2000.tsarevnacache.api.error;

import java.util.Map;

/**
 * Business error carrying an {@link ErrorCode} and optional structured details for the client.
 * Translated into an {@link ApiErrorResponse} by the global exception handler.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, Map.of(), null);
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        this(code, message, details, null);
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
