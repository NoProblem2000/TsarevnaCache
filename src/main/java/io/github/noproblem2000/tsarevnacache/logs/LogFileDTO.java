package io.github.noproblem2000.tsarevnacache.logs;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A log file the viewer may read.
 *
 * @param id             absolute path; pass it as {@code file} to the tail and download endpoints
 * @param name           file name
 * @param configuredPath entry of {@code admin.logs.paths} the file comes from
 * @param exists         whether the file exists (a configured file may not have been written yet)
 * @param readable       whether the admin service may read it
 * @param size           size in bytes
 * @param lastModified   last modification, epoch milliseconds
 * @param applicationLog whether this is the admin service's own log ({@code logging.file.name})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogFileDTO(
        String id,
        String name,
        String configuredPath,
        boolean exists,
        boolean readable,
        Long size,
        Long lastModified,
        boolean applicationLog) {
}
