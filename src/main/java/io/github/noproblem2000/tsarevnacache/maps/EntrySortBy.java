package io.github.noproblem2000.tsarevnacache.maps;

/**
 * Sort order of the IMap data browser. Except {@link #NONE} every order is computed by the admin
 * service over a bounded sample of keys, because hits, cost and access times live in the
 * {@code EntryView} and are invisible to the comparator of a {@code PagingPredicate}.
 */
public enum EntrySortBy {
    /** Partition order (cursor based paging, no sampling). */
    NONE,
    /** {@code EntryView.getHits()}: the hot keys analyzer. */
    HITS,
    /** {@code EntryView.getCost()}: memory cost in bytes. */
    COST,
    /** {@code EntryView.getLastAccessTime()}. */
    LAST_ACCESS,
    /** {@code EntryView.getCreationTime()}. */
    CREATION_TIME,
    /** The deserialized key (numbers numerically, everything else by its text). */
    KEY
}
