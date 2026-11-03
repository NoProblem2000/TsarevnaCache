package io.github.noproblem2000.tsarevnacache.logs;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One line of a log file.
 *
 * @param number       1-based line number; counted from the end ({@code -1} is the last line) when
 *                     the file is too large to count its lines
 * @param text         the line, cut to {@code admin.logs.max-line-length}
 * @param level        level of the record the line belongs to (stack trace lines inherit it);
 *                     {@code null} when unknown
 * @param continuation whether the line continues the record of an earlier line (stack traces,
 *                     multi-line messages)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogLineDTO(long number, String text, LogLevel level, boolean continuation) {
}
