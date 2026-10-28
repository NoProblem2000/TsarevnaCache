package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hazelcast.core.EntryView;

/**
 * {@link EntryView} metadata of a map entry. Times are epoch milliseconds; {@code null} means
 * "not set" (never accessed, never expires, no TTL, ...).
 *
 * @param cost           memory cost of the entry in bytes
 * @param creationTime   creation time
 * @param expirationTime expiration time
 * @param hits           number of hits; {@code null} when per-entry statistics are disabled on the map
 * @param lastAccessTime last access time
 * @param lastStoredTime last time the entry was stored by a MapStore
 * @param lastUpdateTime last update time
 * @param version        entry version
 * @param ttl            time-to-live in milliseconds
 * @param maxIdle        max idle time in milliseconds
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EntryMetadataDTO(
        Long cost,
        Long creationTime,
        Long expirationTime,
        Long hits,
        Long lastAccessTime,
        Long lastStoredTime,
        Long lastUpdateTime,
        Long version,
        Long ttl,
        Long maxIdle) {

    public static EntryMetadataDTO from(EntryView<?, ?> view) {
        return from(view, view.getMaxIdle());
    }

    /**
     * @param maxIdle max idle reported separately by the get-entry-view protocol response
     */
    public static EntryMetadataDTO from(EntryView<?, ?> view, long maxIdle) {
        return new EntryMetadataDTO(
                view.getCost(),
                time(view.getCreationTime()),
                time(view.getExpirationTime()),
                // -1 when per-entry statistics are disabled (the default since Hazelcast 4.2)
                view.getHits() < 0 ? null : view.getHits(),
                time(view.getLastAccessTime()),
                time(view.getLastStoredTime()),
                time(view.getLastUpdateTime()),
                view.getVersion(),
                duration(view.getTtl()),
                duration(maxIdle));
    }

    private static Long time(long value) {
        return value <= 0 || value == Long.MAX_VALUE ? null : value;
    }

    private static Long duration(long value) {
        return value <= 0 || value == Long.MAX_VALUE || value == Integer.MAX_VALUE ? null : value;
    }
}
