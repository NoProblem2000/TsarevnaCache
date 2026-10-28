package io.github.noproblem2000.tsarevnacache.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Uniform error body of the API.
 *
 * @param timestamp moment the error was produced
 * @param status    HTTP status code
 * @param code      error code used by the frontend for localization
 * @param message   English fallback message (never contains stack traces)
 * @param path      request path
 * @param details   optional structured details, e.g. invalid fields or the missing class name
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        ErrorCode code,
        String message,
        String path,
        Map<String, Object> details) {

    public static ApiErrorResponse of(ErrorCode code, String message, String path) {
        return of(code, message, path, Map.of());
    }

    public static ApiErrorResponse of(ErrorCode code, String message, String path, Map<String, Object> details) {
        return of(code, code.status().value(), message, path, details);
    }

    /** Variant with an explicit HTTP status (framework errors such as 415 keep their own status). */
    public static ApiErrorResponse of(ErrorCode code, int status, String message, String path, Map<String, Object> details) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, details);
    }
}
