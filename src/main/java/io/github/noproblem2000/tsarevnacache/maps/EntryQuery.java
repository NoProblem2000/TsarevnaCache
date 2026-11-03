package io.github.noproblem2000.tsarevnacache.maps;

import io.github.noproblem2000.tsarevnacache.api.dto.DataType;

/**
 * Parameters of {@code GET /api/maps/{mapName}/entries}.
 *
 * @param page          zero based page (predicate and sorted mode)
 * @param size          page size
 * @param cursor        scan cursor (unfiltered mode)
 * @param searchKey     exact key to look up
 * @param keyType       type of {@code searchKey}, {@link DataType#STRING} when omitted
 * @param keyClassName  class of {@code searchKey} for {@link DataType#CLASS}
 * @param keyRef        serialized key to look up (alternative to {@code searchKey})
 * @param sqlPredicate  Hazelcast SQL predicate, e.g. {@code age > 25 AND active = true}
 * @param sortBy        order of the entries, {@link EntrySortBy#NONE} when omitted
 * @param sortDirection direction of {@code sortBy}, {@link SortDirection#DESC} when omitted
 * @param scanLimit     maximum number of keys ranked by a sorted request; {@code null} for the default
 */
public record EntryQuery(
        int page,
        int size,
        String cursor,
        String searchKey,
        DataType keyType,
        String keyClassName,
        String keyRef,
        String sqlPredicate,
        EntrySortBy sortBy,
        SortDirection sortDirection,
        Integer scanLimit) {

    /** An unsorted query. */
    public EntryQuery(int page, int size, String cursor, String searchKey, DataType keyType, String keyClassName,
                      String keyRef, String sqlPredicate) {
        this(page, size, cursor, searchKey, keyType, keyClassName, keyRef, sqlPredicate, EntrySortBy.NONE,
                SortDirection.DESC, null);
    }

    /** Whether the entries are ranked by the admin service instead of being paged in partition order. */
    public boolean sorted() {
        return sortBy != null && sortBy != EntrySortBy.NONE;
    }
}
