package io.github.noproblem2000.tsarevnacache.api.dto;

import java.util.Locale;

/**
 * Formats byte counts as {@code B}, {@code KB}, {@code MB}, {@code GB} or {@code TB} (base 1024).
 */
public final class ByteSize {

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB", "PB"};

    private ByteSize() {
    }

    public static String format(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, value >= 100 ? "%.0f %s" : "%.2f %s", value, UNITS[unit]);
    }
}
