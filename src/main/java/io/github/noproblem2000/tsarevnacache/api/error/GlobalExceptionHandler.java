package io.github.noproblem2000.tsarevnacache.api.error;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.hazelcast.client.HazelcastClientOfflineException;
import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.core.OperationTimeoutException;
import com.hazelcast.query.QueryException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates exceptions into {@link ApiErrorResponse} bodies with stable error codes. Stack traces
 * are logged, never returned.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException e, HttpServletRequest request) {
        if (e.getCode().status().is5xxServerError()) {
            log.warn("{} on {}: {}", e.getCode(), request.getRequestURI(), e.getMessage());
        } else {
            log.debug("{} on {}: {}", e.getCode(), request.getRequestURI(), e.getMessage());
        }
        return respond(e.getCode(), e.getMessage(), request, e.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(MethodArgumentNotValidException e,
                                                                 HttpServletRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, Map.of("fields", fields));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleParameterValidation(HandlerMethodValidationException e,
                                                                      HttpServletRequest request) {
        List<String> problems = e.getAllValidationResults().stream()
                .map(result -> result.getMethodParameter().getParameterName() + ": "
                        + result.getResolvableErrors().stream()
                        .map(error -> String.valueOf(error.getDefaultMessage()))
                        .reduce((a, b) -> a + ", " + b).orElse("invalid"))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, Map.of("problems", problems));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException e,
                                                                      HttpServletRequest request) {
        List<String> problems = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, Map.of("problems", problems));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleBadParameter(Exception e, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED, e.getMessage(), request, Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException e,
                                                             HttpServletRequest request) {
        if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            // well-formed JSON with a wrong value, e.g. an unknown enum constant for "valueType"
            String field = mismatch.getPath().stream()
                    .map(reference -> reference.getFieldName() != null ? reference.getFieldName() : "[" + reference.getIndex() + "]")
                    .collect(Collectors.joining("."));
            return respond(ErrorCode.VALIDATION_FAILED, "Invalid value for '" + field + "'", request,
                    Map.of("fields", Map.of(field, mismatch.getOriginalMessage())));
        }
        return respond(ErrorCode.INVALID_JSON, "Malformed request body", request, Map.of());
    }

    @ExceptionHandler(QueryException.class)
    public ResponseEntity<ApiErrorResponse> handleQuery(QueryException e, HttpServletRequest request) {
        return respond(ErrorCode.INVALID_PREDICATE, e.getMessage(), request, Map.of());
    }

    @ExceptionHandler({HazelcastClientOfflineException.class, HazelcastInstanceNotActiveException.class})
    public ResponseEntity<ApiErrorResponse> handleOffline(RuntimeException e, HttpServletRequest request) {
        log.warn("Cluster unavailable on {}: {}", request.getRequestURI(), e.getMessage());
        return respond(ErrorCode.CLUSTER_UNAVAILABLE, "The cluster is not reachable", request, Map.of());
    }

    @ExceptionHandler(OperationTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleTimeout(OperationTimeoutException e, HttpServletRequest request) {
        log.warn("Cluster timeout on {}: {}", request.getRequestURI(), e.getMessage());
        return respond(ErrorCode.CLUSTER_TIMEOUT, "The cluster did not answer in time", request, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        return respond(ErrorCode.NOT_FOUND, "Not found", request, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e,
                                                         HttpServletRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, e.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        if (e instanceof ErrorResponse framework && framework.getStatusCode().is4xxClientError()) {
            // other Spring MVC client errors (415, 406, ...) keep their status instead of becoming a 500
            int status = framework.getStatusCode().value();
            ErrorCode code = status == 404 ? ErrorCode.NOT_FOUND : ErrorCode.VALIDATION_FAILED;
            return ResponseEntity.status(status)
                    .body(ApiErrorResponse.of(code, status, e.getMessage(), request.getRequestURI(), Map.of()));
        }
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(ErrorCode.INTERNAL_ERROR, "Unexpected server error", request, Map.of());
    }

    private static ResponseEntity<ApiErrorResponse> respond(ErrorCode code, String message, HttpServletRequest request,
                                                            Map<String, Object> details) {
        return ResponseEntity.status(code.status())
                .body(ApiErrorResponse.of(code, message, request.getRequestURI(), details));
    }
}
