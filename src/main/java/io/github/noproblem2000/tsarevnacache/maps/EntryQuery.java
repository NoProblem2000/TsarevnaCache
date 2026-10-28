package io.github.noproblem2000.tsarevnacache.maps;

import io.github.noproblem2000.tsarevnacache.api.dto.DataType;

/**
 * Parameters of {@code GET /api/maps/{mapName}/entries}.
 *
 * @param page         zero based page (predicate mode)
 * @param size         page size
 * @param cursor       scan cursor (unfiltered mode)
 * @param searchKey    exact key to look up
 * @param keyType      type of {@code searchKey}, {@link DataType#STRING} when omitted
 * @param keyClassName class of {@code searchKey} for {@link DataType#CLASS}
 * @param keyRef       serialized key to look up (alternative to {@code searchKey})
 * @param sqlPredicate Hazelcast SQL predicate, e.g. {@code age > 25 AND active = true}
 */
public record EntryQuery(
        int page,
        int size,
        String cursor,
        String searchKey,
        DataType keyType,
        String keyClassName,
        String keyRef,
        String sqlPredicate) {
}
