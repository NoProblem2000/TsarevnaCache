package io.github.noproblem2000.tsarevnacache.logs;

import java.util.List;

/**
 * Log files of the viewer.
 *
 * @param files           existing files first (the application log first), then missing configured files
 * @param configuredPaths {@code admin.logs.paths} as configured
 * @param defaultLines    lines of a tail request without {@code lines}
 * @param maxLines        maximum {@code lines} of a tail request
 */
public record LogFilesDTO(List<LogFileDTO> files, List<String> configuredPaths, int defaultLines, int maxLines) {
}
