package io.github.noproblem2000.tsarevnacache.logs;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The end of a log file, filtered.
 *
 * @param file             the file
 * @param lines            matching lines in file order (the last line of the file last)
 * @param requestedLines   applied line limit
 * @param totalLines       number of lines of the file; {@code null} when it is too large to count
 * @param scannedBytes     bytes read from the end of the file
 * @param reachedStart     whether the whole file was read
 * @param scanLimitReached whether reading stopped at {@code admin.logs.scan-limit} before enough lines matched
 * @param level            applied level filter
 * @param search           applied search text
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogTailDTO(
        LogFileDTO file,
        List<LogLineDTO> lines,
        int requestedLines,
        Long totalLines,
        long scannedBytes,
        boolean reachedStart,
        boolean scanLimitReached,
        LogLevelFilter level,
        String search) {
}
