package io.github.noproblem2000.tsarevnacache.maps;

import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Position of an unfiltered IMap scan: the partition being read and Hazelcast's iteration pointers
 * inside it. Encoded as an opaque URL-safe string for the client.
 *
 * @param partitionId partition currently scanned
 * @param pointers    iteration pointers as (index, size) pairs, as used by {@code MapFetchKeysCodec}
 */
record ScanCursor(int partitionId, List<Map.Entry<Integer, Integer>> pointers) {

    private static final String VERSION = "v1";

    /** Start of a partition. */
    static ScanCursor start(int partitionId) {
        return new ScanCursor(partitionId, List.of(new SimpleImmutableEntry<>(Integer.MAX_VALUE, -1)));
    }

    /** Whether Hazelcast reported the partition as fully iterated. */
    static boolean isExhausted(List<Map.Entry<Integer, Integer>> pointers) {
        return pointers.isEmpty() || pointers.get(pointers.size() - 1).getKey() < 0;
    }

    String encode() {
        String text = VERSION + ":" + partitionId + ":" + pointers.stream()
                .map(pointer -> pointer.getKey() + "," + pointer.getValue())
                .collect(Collectors.joining(";"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws ApiException with {@link ErrorCode#INVALID_CURSOR} for malformed or foreign cursors
     */
    static ScanCursor decode(String cursor, int partitionCount) {
        try {
            String text = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            String[] parts = text.split(":", 3);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("Unknown cursor format");
            }
            int partitionId = Integer.parseInt(parts[1]);
            if (partitionId < 0 || partitionId >= partitionCount) {
                throw new IllegalArgumentException("Partition out of range");
            }
            List<Map.Entry<Integer, Integer>> pointers = new ArrayList<>();
            for (String pair : parts[2].split(";")) {
                String[] values = pair.split(",");
                pointers.add(new SimpleImmutableEntry<>(Integer.parseInt(values[0]), Integer.parseInt(values[1])));
            }
            return new ScanCursor(partitionId, List.copyOf(pointers));
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.INVALID_CURSOR, "Invalid cursor: " + e.getMessage(), Map.of(), e);
        }
    }
}
