package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;

/**
 * {@code LocalMapStats} of one map, aggregated over all members (counters summed, creation time
 * minimum, access/update times maximum) plus the per-member breakdown.
 *
 * <p>Latencies are totals in milliseconds; divide by the matching operation count for averages.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapStatsDTO(
        String name,
        long ownedEntryCount,
        long backupEntryCount,
        long backupCount,
        long ownedEntryMemoryCost,
        String ownedEntryMemoryCostFormatted,
        long backupEntryMemoryCost,
        String backupEntryMemoryCostFormatted,
        long heapCost,
        String heapCostFormatted,
        long merkleTreesCost,
        long hits,
        long getOperationCount,
        long putOperationCount,
        long setOperationCount,
        long removeOperationCount,
        long numberOfOtherOperations,
        long numberOfEvents,
        long evictionCount,
        long expirationCount,
        long lockedEntryCount,
        long dirtyEntryCount,
        long queryCount,
        long indexedQueryCount,
        long totalGetLatency,
        long totalPutLatency,
        long totalSetLatency,
        long totalRemoveLatency,
        long maxGetLatency,
        long maxPutLatency,
        long maxSetLatency,
        long maxRemoveLatency,
        Long creationTime,
        Long lastAccessTime,
        Long lastUpdateTime,
        NearCache nearCache,
        List<Member> members,
        StatsStatusDTO stats) {

    /**
     * Near cache statistics (present only when a near cache is configured on the members).
     */
    public record NearCache(
            long ownedEntryCount,
            long ownedEntryMemoryCost,
            long hits,
            long misses,
            double ratio,
            long evictions,
            long expirations,
            long invalidations) {
    }

    /**
     * Per-member slice of the statistics.
     */
    public record Member(
            String uuid,
            String address,
            long ownedEntryCount,
            long backupEntryCount,
            long ownedEntryMemoryCost,
            long backupEntryMemoryCost,
            long hits,
            long getOperationCount,
            long putOperationCount,
            long setOperationCount,
            long removeOperationCount) {
    }
}
