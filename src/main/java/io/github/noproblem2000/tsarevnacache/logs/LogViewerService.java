package io.github.noproblem2000.tsarevnacache.logs;

import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.config.properties.LogViewerProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Server side log files: lists the files of {@code admin.logs.paths}, returns the filtered end of
 * a file and opens a file for download.
 *
 * <p>Files are never loaded as a whole: the tail is read backwards with a {@link ReverseLineReader}
 * until enough lines match or {@code admin.logs.scan-limit} bytes were read. Only files listed by
 * {@link #files()} can be opened, so the {@code file} parameter cannot reach any other file.
 *
 * <p>Lines are grouped into records: a line naming a level or starting with a timestamp opens a
 * record, every other line (stack traces, wrapped messages) continues the record above and takes
 * its level. The date line of the two-line java.util.logging format takes the level of the
 * {@code INFO: ...} line below it.
 */
@Slf4j
@Service
public class LogViewerService {

    /** Files of a configured directory the viewer lists (rotations like {@code app.log.3} included). */
    private static final Pattern LOG_FILE_NAME = Pattern.compile("(?i).+\\.(log|txt|out)(\\.\\d+)?");

    /**
     * Timestamps that start a record: ISO dates, {@code 10:00:00.123} and java.util.logging dates in
     * any locale ({@code Nov 03, 2026 10:00:00 AM}, {@code нояб. 03, 2026 10:00:00}).
     */
    private static final Pattern TIMESTAMP = Pattern.compile(
            "\\s*\\[?(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}|\\d{2}:\\d{2}:\\d{2}[.,]\\d{3}"
                    + "|[^\\s\\d]{3,6}\\.? \\d{1,2}, \\d{4} \\d{1,2}:\\d{2})");

    private static final int COUNT_BUFFER = 1 << 20;

    /** Continuation lines collected while looking for the header of their record. */
    private static final int MAX_PENDING_LINES = 1000;

    private final LogViewerProperties properties;
    private final Path applicationLog;

    public LogViewerService(LogViewerProperties properties, @Value("${logging.file.name:}") String loggingFile) {
        this.properties = properties;
        this.applicationLog = StringUtils.hasText(loggingFile) ? absolute(loggingFile) : null;
    }

    // ------------------------------------------------------------------ files

    public LogFilesDTO files() {
        return new LogFilesDTO(listFiles(), List.copyOf(properties.getPaths()), properties.getDefaultLines(),
                properties.getMaxLines());
    }

    /**
     * Files of {@code admin.logs.paths}: the application log first, then existing files by last
     * modification (newest first), then configured files that do not exist yet.
     */
    List<LogFileDTO> listFiles() {
        Map<String, LogFileDTO> files = new LinkedHashMap<>();
        for (String configured : properties.getPaths()) {
            if (!StringUtils.hasText(configured)) {
                continue;
            }
            Path path;
            try {
                path = absolute(configured.trim());
            } catch (InvalidPathException e) {
                log.warn("Ignoring invalid log viewer path '{}': {}", configured, e.getMessage());
                continue;
            }
            if (Files.isDirectory(path)) {
                try (Stream<Path> children = Files.list(path)) {
                    children.filter(Files::isRegularFile)
                            .filter(child -> LOG_FILE_NAME.matcher(child.getFileName().toString()).matches())
                            .forEach(child -> files.putIfAbsent(child.toString(), describe(child, configured)));
                } catch (IOException e) {
                    log.warn("Cannot list log directory {}: {}", path, e.getMessage());
                }
            } else {
                files.putIfAbsent(path.toString(), describe(path, configured));
            }
        }
        List<LogFileDTO> sorted = new ArrayList<>(files.values());
        sorted.sort(Comparator.comparing((LogFileDTO file) -> !file.exists())
                .thenComparing(file -> !file.applicationLog())
                .thenComparing(file -> file.lastModified() == null ? 0 : -file.lastModified()));
        return sorted;
    }

    private LogFileDTO describe(Path path, String configured) {
        boolean exists = Files.isRegularFile(path);
        Long size = null;
        Long modified = null;
        if (exists) {
            try {
                size = Files.size(path);
                modified = Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
                exists = false;
            }
        }
        return new LogFileDTO(path.toString(), path.getFileName() == null ? path.toString()
                : path.getFileName().toString(), configured, exists, exists && Files.isReadable(path), size, modified,
                path.equals(applicationLog));
    }

    /**
     * The listed file with this id; without an id the first existing file.
     *
     * @throws ApiException with {@link ErrorCode#LOG_FILE_NOT_FOUND} when the file is not listed or
     *                      does not exist, {@link ErrorCode#LOG_FILE_UNREADABLE} when it cannot be read
     */
    public LogFileDTO requireFile(String id) {
        List<LogFileDTO> files = listFiles();
        LogFileDTO file;
        if (!StringUtils.hasText(id)) {
            file = files.stream().filter(LogFileDTO::exists).findFirst().orElseThrow(() -> new ApiException(
                    ErrorCode.LOG_FILE_NOT_FOUND, "None of the configured log files exists",
                    Map.of("paths", properties.getPaths())));
        } else {
            String wanted;
            try {
                wanted = absolute(id.trim()).toString();
            } catch (InvalidPathException e) {
                throw notListed(id);
            }
            file = files.stream().filter(candidate -> candidate.id().equals(wanted) && candidate.exists())
                    .findFirst().orElseThrow(() -> notListed(id));
        }
        if (!file.readable()) {
            throw new ApiException(ErrorCode.LOG_FILE_UNREADABLE, "The log file cannot be read",
                    Map.of("file", file.id()));
        }
        return file;
    }

    /** Opens a listed file for download. */
    public InputStream open(LogFileDTO file) {
        try {
            return Files.newInputStream(Path.of(file.id()));
        } catch (IOException e) {
            throw unreadable(file, e);
        }
    }

    // ------------------------------------------------------------------ tail

    /**
     * The last {@code lines} lines of a file that pass the level filter and contain
     * {@code search} (case-insensitive).
     *
     * @param lines {@code null} for {@code admin.logs.default-lines}, capped at {@code admin.logs.max-lines}
     */
    public LogTailDTO tail(String fileId, Integer lines, LogLevelFilter level, String search) {
        LogFileDTO file = requireFile(fileId);
        int limit = Math.max(1, Math.min(lines == null ? properties.getDefaultLines() : lines,
                properties.getMaxLines()));
        LogLevelFilter filter = level == null ? LogLevelFilter.ALL : level;
        String needle = StringUtils.hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;
        Path path = Path.of(file.id());
        try {
            long length = Files.size(path);
            Long totalLines = length <= properties.getLineNumberLimit().toBytes() ? countLines(path, length) : null;
            Tail tail = new Tail(filter, needle, limit, totalLines == null ? -1 : totalLines);
            long scanLimit = properties.getScanLimit().toBytes();
            boolean scanLimitReached = false;
            try (ReverseLineReader reader = new ReverseLineReader(path, length, maxLineBytes())) {
                String line;
                while (!tail.full() && (line = reader.previous()) != null) {
                    tail.accept(line);
                    // lines of a chunk already read are free, only the next chunk counts against the limit
                    if (!tail.full() && reader.scannedBytes() >= scanLimit && !reader.hasBufferedLines()
                            && !reader.reachedStart()) {
                        scanLimitReached = true;
                        break;
                    }
                }
                tail.finish();
                LogFileDTO current = describe(path, file.configuredPath());
                return new LogTailDTO(current, tail.result(), limit, totalLines, reader.scannedBytes(),
                        reader.reachedStart(), scanLimitReached, filter, needle == null ? null : search.trim());
            }
        } catch (IOException e) {
            throw unreadable(file, e);
        }
    }

    /** Collects the matching lines while the file is read backwards, record by record. */
    private final class Tail {

        private final LogLevelFilter filter;
        private final String needle;
        private final int limit;
        /** Lines in reverse order (the last line of the file first). */
        private final List<LogLineDTO> lines = new ArrayList<>();
        /** Continuation lines read since the last record header, the latest first. */
        private final List<Numbered> pending = new ArrayList<>();
        private long number;
        private LogLevel levelBelow;
        private boolean headerBelowHadTimestamp = true;

        Tail(LogLevelFilter filter, String needle, int limit, long lastNumber) {
            this.filter = filter;
            this.needle = needle;
            this.limit = limit;
            this.number = lastNumber;
        }

        boolean full() {
            return lines.size() >= limit;
        }

        void accept(String line) {
            Numbered current = new Numbered(number--, cut(line));
            LogLevel level = LogLevel.detect(current.text());
            boolean timestamp = TIMESTAMP.matcher(current.text()).lookingAt();
            if (level == null && !timestamp) {
                pending.add(current);
                if (pending.size() >= Math.max(limit, MAX_PENDING_LINES)) {
                    // plain text without record headers: do not read the whole file looking for one
                    emit(null, null);
                }
                return;
            }
            if (level == null && !headerBelowHadTimestamp) {
                // "Nov 03, 2026 10:00:00 AM com.hazelcast..." above "INFO: ..." (java.util.logging)
                level = levelBelow;
            }
            emit(current, level);
            levelBelow = level;
            headerBelowHadTimestamp = timestamp;
        }

        /** Lines above the first record header that was read keep an unknown level. */
        void finish() {
            if (!full() && !pending.isEmpty()) {
                emit(null, null);
            }
        }

        private void emit(Numbered header, LogLevel level) {
            if (filter.accepts(level)) {
                for (Numbered line : pending) {
                    add(line, level, header != null);
                }
                if (header != null) {
                    add(header, level, false);
                }
            }
            pending.clear();
        }

        private void add(Numbered line, LogLevel level, boolean continuation) {
            if (full() || needle != null && !line.text().toLowerCase(Locale.ROOT).contains(needle)) {
                return;
            }
            lines.add(new LogLineDTO(line.number(), line.text(), level, continuation));
        }

        List<LogLineDTO> result() {
            List<LogLineDTO> ordered = new ArrayList<>(lines);
            Collections.reverse(ordered);
            return ordered;
        }
    }

    private record Numbered(long number, String text) {
    }

    private String cut(String line) {
        int max = properties.getMaxLineLength();
        return line.length() <= max ? line : line.substring(0, max) + " …";
    }

    private int maxLineBytes() {
        // up to 4 bytes per character in UTF-8
        return properties.getMaxLineLength() * 4;
    }

    /** Lines of the first {@code length} bytes: newlines plus an unterminated last line. */
    static long countLines(Path path, long length) throws IOException {
        if (length == 0) {
            return 0;
        }
        long newlines = 0;
        byte last = 0;
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(COUNT_BUFFER);
            long position = 0;
            while (position < length) {
                buffer.clear();
                buffer.limit((int) Math.min(COUNT_BUFFER, length - position));
                int read = channel.read(buffer, position);
                if (read <= 0) {
                    break;
                }
                byte[] bytes = buffer.array();
                for (int i = 0; i < read; i++) {
                    if (bytes[i] == '\n') {
                        newlines++;
                    }
                }
                last = bytes[read - 1];
                position += read;
            }
        }
        return last == '\n' ? newlines : newlines + 1;
    }

    // ------------------------------------------------------------------ helpers

    private static Path absolute(String path) {
        return Path.of(path).toAbsolutePath().normalize();
    }

    private static ApiException notListed(String id) {
        return new ApiException(ErrorCode.LOG_FILE_NOT_FOUND,
                "The file is not one of the log files configured in admin.logs.paths", Map.of("file", id));
    }

    private static ApiException unreadable(LogFileDTO file, IOException e) {
        return new ApiException(ErrorCode.LOG_FILE_UNREADABLE, "The log file cannot be read: " + e.getMessage(),
                Map.of("file", file.id()), e);
    }
}
