package io.github.noproblem2000.tsarevnacache.maps;

import com.fasterxml.jackson.databind.JsonNode;
import com.hazelcast.internal.serialization.Data;
import io.github.noproblem2000.tsarevnacache.api.dto.InspectedValue;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.ToLongFunction;

/**
 * The {@code EntryView} metrics of one sampled entry, without its value, as ranked by the hot
 * keys analyzer.
 *
 * @param key            serialized key
 * @param keyRef         {@code key} as returned to the UI; breaks ties so the ranking is stable
 * @param hits           hit count, {@code -1} when per-entry statistics are disabled on the map
 * @param cost           memory cost in bytes
 * @param lastAccessTime last access, {@code 0} when never accessed
 * @param creationTime   creation time
 * @param keyOrder       comparable form of the key ({@link EntrySortBy#KEY} only)
 */
record EntryStats(
        Data key,
        String keyRef,
        long hits,
        long cost,
        long lastAccessTime,
        long creationTime,
        KeyOrder keyOrder) {

    /** Ranking order; ties are broken by {@code keyRef} in ascending order whatever the direction. */
    static Comparator<EntryStats> comparator(EntrySortBy sortBy, SortDirection direction) {
        Comparator<EntryStats> order = switch (sortBy) {
            case HITS -> metric(EntryStats::hits);
            case COST -> metric(EntryStats::cost);
            case LAST_ACCESS -> metric(EntryStats::lastAccessTime);
            case CREATION_TIME -> metric(EntryStats::creationTime);
            case KEY -> Comparator.comparing(EntryStats::keyOrder);
            case NONE -> (left, right) -> 0;
        };
        if (direction == SortDirection.DESC) {
            order = order.reversed();
        }
        return order.thenComparing(EntryStats::keyRef);
    }

    private static Comparator<EntryStats> metric(ToLongFunction<EntryStats> metric) {
        return Comparator.comparingLong(metric);
    }

    /**
     * Comparable form of a key: numbers before text, numbers numerically, text case-insensitively in
     * natural order ({@code order-2} before {@code order-10}); keys that could not be deserialized
     * come last.
     */
    record KeyOrder(int group, BigDecimal number, String text) implements Comparable<KeyOrder> {

        private static final Comparator<KeyOrder> ORDER = Comparator
                .comparingInt(KeyOrder::group)
                .thenComparing(KeyOrder::number, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(KeyOrder::text, Comparator.nullsLast(KeyOrder::compareNatural));

        /** Compares runs of digits by their numeric value and everything else character by character. */
        static int compareNatural(String left, String right) {
            int i = 0;
            int j = 0;
            while (i < left.length() && j < right.length()) {
                char a = left.charAt(i);
                char b = right.charAt(j);
                if (Character.isDigit(a) && Character.isDigit(b)) {
                    int endA = digitsEnd(left, i);
                    int endB = digitsEnd(right, j);
                    int order = new BigDecimal(left.substring(i, endA)).compareTo(new BigDecimal(right.substring(j, endB)));
                    if (order != 0) {
                        return order;
                    }
                    // equal numbers: fewer leading zeros first
                    if (endA - i != endB - j) {
                        return Integer.compare(endA - i, endB - j);
                    }
                    i = endA;
                    j = endB;
                } else {
                    if (a != b) {
                        return Character.compare(a, b);
                    }
                    i++;
                    j++;
                }
            }
            return Integer.compare(left.length() - i, right.length() - j);
        }

        private static int digitsEnd(String text, int start) {
            int end = start;
            while (end < text.length() && Character.isDigit(text.charAt(end))) {
                end++;
            }
            return end;
        }

        static KeyOrder of(InspectedValue key) {
            if (!key.deserialized()) {
                return new KeyOrder(2, null, null);
            }
            JsonNode value = key.value();
            if (value != null && value.isNumber()) {
                return new KeyOrder(0, value.decimalValue(), null);
            }
            String text = value != null && value.isTextual() ? value.asText()
                    : key.preview() == null ? "" : key.preview();
            return new KeyOrder(1, null, text.toLowerCase(Locale.ROOT));
        }

        @Override
        public int compareTo(KeyOrder other) {
            return ORDER.compare(this, other);
        }
    }
}
