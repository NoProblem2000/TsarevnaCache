package io.github.noproblem2000.tsarevnacache.dto.parser;

/**
 * One {@code .java} file of a batch import.
 *
 * @param fileName file name (or relative path when a folder was selected), used in the report
 * @param content  the file's text
 */
public record JavaFileSourceDTO(String fileName, String content) {
}
