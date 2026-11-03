package io.github.noproblem2000.tsarevnacache.logs;

/**
 * Level filter of the log viewer. A level keeps its own records and the more severe ones
 * ({@code WARN} shows warnings and errors); {@link #ALL} also keeps lines without a level.
 */
public enum LogLevelFilter {
    ALL(null),
    ERROR(LogLevel.ERROR),
    WARN(LogLevel.WARN),
    INFO(LogLevel.INFO),
    DEBUG(LogLevel.DEBUG);

    private final LogLevel threshold;

    LogLevelFilter(LogLevel threshold) {
        this.threshold = threshold;
    }

    public boolean accepts(LogLevel level) {
        return threshold == null || level != null && level.atLeast(threshold);
    }
}
