package io.github.noproblem2000.tsarevnacache.maps;

/** Direction of an {@link EntrySortBy} order. */
public enum SortDirection {
    /** Largest first (most hits, highest cost, most recent). */
    DESC,
    /** Smallest first (cold keys, cheapest, oldest). */
    ASC
}
