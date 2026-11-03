package io.github.noproblem2000.tsarevnacache.logs;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One event of the live stream.
 *
 * @param seq        increasing sequence number; ask for events {@code after} the last one seen
 * @param timestamp  epoch milliseconds
 * @param level      severity
 * @param source     origin
 * @param logger     logger name (log events) or the listener that produced the event
 * @param thread     thread that produced the event
 * @param message    the message
 * @param stackTrace stack trace of the attached exception, if any
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiveEventDTO(
        long seq,
        long timestamp,
        LogLevel level,
        LiveEventSource source,
        String logger,
        String thread,
        String message,
        String stackTrace) {
}
