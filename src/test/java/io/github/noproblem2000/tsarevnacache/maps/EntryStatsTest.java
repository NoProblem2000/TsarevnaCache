package io.github.noproblem2000.tsarevnacache.maps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntryStatsTest {

    @Test
    void textKeysSortInNaturalOrder() {
        List<String> keys = new ArrayList<>(List.of("c10", "c2", "c1", "c02", "b", "product-100", "product-20", "c"));
        keys.sort(EntryStats.KeyOrder::compareNatural);
        assertThat(keys).containsExactly("b", "c", "c1", "c2", "c02", "c10", "product-20", "product-100");
    }

    @Test
    void rankingIsStableAndDirectional() {
        EntryStats hot = stats("a", 10, 100);
        EntryStats cold = stats("b", 0, 300);
        EntryStats tie = stats("c", 10, 200);
        List<EntryStats> ranking = new ArrayList<>(List.of(cold, tie, hot));

        ranking.sort(EntryStats.comparator(EntrySortBy.HITS, SortDirection.DESC));
        assertThat(ranking).containsExactly(hot, tie, cold);
        ranking.sort(EntryStats.comparator(EntrySortBy.HITS, SortDirection.ASC));
        assertThat(ranking).containsExactly(cold, hot, tie);
        ranking.sort(EntryStats.comparator(EntrySortBy.COST, SortDirection.DESC));
        assertThat(ranking).containsExactly(cold, tie, hot);
    }

    private static EntryStats stats(String keyRef, long hits, long cost) {
        return new EntryStats(null, keyRef, hits, cost, 0, 0, null);
    }
}
