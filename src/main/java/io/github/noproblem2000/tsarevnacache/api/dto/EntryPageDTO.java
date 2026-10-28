package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * One page of map entries.
 *
 * @param mode       how the page was selected
 * @param page       zero based page index (echoed for {@link SearchMode#ALL}, which pages by cursor)
 * @param size       requested page size
 * @param hasNext    whether another page may follow
 * @param nextCursor cursor of the next page ({@link SearchMode#ALL} only)
 * @param total      total number of entries in the map ({@link SearchMode#ALL} only)
 * @param entries    entries of this page
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EntryPageDTO(
        SearchMode mode,
        int page,
        int size,
        boolean hasNext,
        String nextCursor,
        Long total,
        List<EntryDTO> entries) {

    /** Entry selection mode. */
    public enum SearchMode {
        /** Cursor based scan over all partitions. */
        ALL,
        /** Exact key lookup. */
        KEY,
        /** Paging over the results of an SQL predicate. */
        PREDICATE
    }
}
