package io.github.noproblem2000.tsarevnacache.logs;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Severity of a log line, detected from its text (Logback, Log4j and java.util.logging formats). */
public enum LogLevel {
    DEBUG(10),
    INFO(20),
    WARN(30),
    ERROR(40);

    /** Level tokens are only searched near the start of a line, where every common layout puts them. */
    private static final int HEADER_LENGTH = 200;

    private static final Pattern TOKEN = Pattern.compile(
            "(?<![\\w.$-])(FATAL|SEVERE|ERROR|WARNING|WARN|INFO|CONFIG|DEBUG|FINEST|FINER|FINE|TRACE)(?![\\w$-])");

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    /** Whether this level is at least as severe as {@code threshold}. */
    public boolean atLeast(LogLevel threshold) {
        return severity >= threshold.severity;
    }

    /**
     * Level named near the start of a line, {@code null} when there is none (stack trace lines,
     * wrapped messages, the date line of the two-line java.util.logging format, ...).
     */
    public static LogLevel detect(String line) {
        Matcher matcher = TOKEN.matcher(line);
        matcher.region(0, Math.min(line.length(), HEADER_LENGTH));
        return matcher.find() ? of(matcher.group(1)) : null;
    }

    /** Maps a level name of any common logging framework, {@code null} for unknown names. */
    public static LogLevel of(String name) {
        if (name == null) {
            return null;
        }
        return switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "FATAL", "SEVERE", "ERROR" -> ERROR;
            case "WARNING", "WARN" -> WARN;
            case "INFO", "CONFIG" -> INFO;
            case "DEBUG", "TRACE", "FINE", "FINER", "FINEST", "ALL" -> DEBUG;
            default -> null;
        };
    }
}
