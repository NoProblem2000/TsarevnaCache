package io.github.noproblem2000.tsarevnacache.api.dto;

import java.util.List;

/**
 * One page of map entries.
 *
 * @param mode    how the page was selected
 * @param page    zero based page index
 * @param size    requested page size
 * @param hasNext whether another page may follow
 * @param entries entries of this page
 */
public record EntryPageDTO(
        SearchMode mode,
        int page,
        int size,
        boolean hasNext,
        List<EntryDTO> entries) {

    /** Entry selection mode. */
    public enum SearchMode {
        /** Plain paging over the whole map. */
        ALL,
        /** Exact key lookup. */
        KEY,
        /** Paging over the results of an SQL predicate. */
        PREDICATE
    }
}
