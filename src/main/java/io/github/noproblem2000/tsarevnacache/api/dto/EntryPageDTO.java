package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.noproblem2000.tsarevnacache.maps.EntrySortBy;
import io.github.noproblem2000.tsarevnacache.maps.SortDirection;
import java.util.List;

/**
 * One page of map entries.
 *
 * @param mode          how the page was selected
 * @param page          zero based page index (echoed for {@link SearchMode#ALL}, which pages by cursor)
 * @param size          requested page size
 * @param hasNext       whether another page may follow
 * @param nextCursor    cursor of the next page ({@link SearchMode#ALL} only)
 * @param total         total number of entries in the map ({@link SearchMode#ALL} and unfiltered
 *                      {@link SearchMode#SORTED} only)
 * @param entries       entries of this page
 * @param sortBy        applied order ({@link SearchMode#SORTED} only)
 * @param sortDirection applied direction ({@link SearchMode#SORTED} only)
 * @param sampled       {@code true} when more keys matched than {@code scanLimit}: the ranking is built
 *                      over the first {@code scannedCount} keys only ({@link SearchMode#SORTED} only)
 * @param scannedCount  number of entries the ranking was built from ({@link SearchMode#SORTED} only)
 * @param scanLimit     applied scan limit ({@link SearchMode#SORTED} only)
 * @param maxHits       highest hit count among the ranked entries; {@code null} when per-entry
 *                      statistics are disabled on the map ({@link SearchMode#SORTED} only)
 * @param hitsAvailable {@code false} when no ranked entry reports hits, i.e. per-entry statistics
 *                      are disabled on the map ({@link SearchMode#SORTED} only)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EntryPageDTO(
        SearchMode mode,
        int page,
        int size,
        boolean hasNext,
        String nextCursor,
        Long total,
        List<EntryDTO> entries,
        EntrySortBy sortBy,
        SortDirection sortDirection,
        Boolean sampled,
        Integer scannedCount,
        Integer scanLimit,
        Long maxHits,
        Boolean hitsAvailable) {

    /** An unsorted page. */
    public EntryPageDTO(SearchMode mode, int page, int size, boolean hasNext, String nextCursor, Long total,
                        List<EntryDTO> entries) {
        this(mode, page, size, hasNext, nextCursor, total, entries, null, null, null, null, null, null, null);
    }

    /** Entry selection mode. */
    public enum SearchMode {
        /** Cursor based scan over all partitions. */
        ALL,
        /** Exact key lookup. */
        KEY,
        /** Paging over the results of an SQL predicate. */
        PREDICATE,
        /** Paging over a ranking of sampled entries (optionally filtered by an SQL predicate). */
        SORTED
    }
}
