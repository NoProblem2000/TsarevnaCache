package io.github.noproblem2000.tsarevnacache.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Log viewer ({@code admin.logs.*}): server side log files and the live cluster event stream.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "admin.logs")
public class LogViewerProperties {

    /**
     * Log files or directories the UI may read and download. A directory contributes its
     * {@code *.log}, {@code *.txt}, {@code *.out} files (and numbered rotations such as
     * {@code *.log.1}), not its subdirectories. Relative paths resolve against the working directory.
     * No other file on the server can be opened through the log viewer.
     */
    @NotNull
    private List<String> paths = new ArrayList<>(List.of("./logs/hazelcast-inspector.log"));

    /** Lines returned by a tail request that does not ask for another number. */
    @Min(1)
    private int defaultLines = 500;

    /** Upper bound of the lines a tail request may ask for. */
    @Min(1)
    @Max(100_000)
    private int maxLines = 5000;

    /**
     * Bytes read backwards from the end of a file per tail request at most. Filters that match
     * rarely stop here instead of reading a multi-gigabyte file.
     */
    @NotNull
    private DataSize scanLimit = DataSize.ofMegabytes(64);

    /**
     * Files up to this size get absolute line numbers (the newlines before the tail are counted);
     * larger files are numbered relative to the end.
     */
    @NotNull
    private DataSize lineNumberLimit = DataSize.ofMegabytes(64);

    /** Longer lines are cut to this many characters. */
    @Min(80)
    private int maxLineLength = 4000;

    /** Events kept by the in-memory live stream (the oldest are dropped first). */
    @Min(10)
    @Max(100_000)
    private int liveBufferSize = 1000;

    /** Minimum level of {@code com.hazelcast} log events copied into the live stream. */
    @NotNull
    private String liveHazelcastLevel = "INFO";

    /** Minimum level of the admin application's own log events copied into the live stream. */
    @NotNull
    private String liveApplicationLevel = "WARN";
}
