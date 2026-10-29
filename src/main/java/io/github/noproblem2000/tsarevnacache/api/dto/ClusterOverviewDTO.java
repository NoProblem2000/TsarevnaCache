package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Response of {@code GET /api/cluster/overview}.
 *
 * @param connected       whether the admin client currently has a cluster connection
 * @param clusterName     configured cluster name
 * @param clientVersion   Hazelcast version of the admin client
 * @param clusterVersion  Hazelcast version of the master member
 * @param members         cluster members, master first
 * @param totals          aggregated data statistics
 * @param structureCounts number of (non-internal) distributed objects per type
 * @param topMaps         the five maps with the highest owned entry memory cost
 * @param stats           availability of the metrics behind the statistics
 * @param appVersion      version of this admin service (the release it was built from)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClusterOverviewDTO(
        boolean connected,
        String clusterName,
        String clientVersion,
        String clusterVersion,
        List<MemberDTO> members,
        Totals totals,
        Map<StructureType, Integer> structureCounts,
        List<MapMemory> topMaps,
        StatsStatusDTO stats,
        String appVersion) {

    /**
     * Aggregated statistics over all maps.
     *
     * @param distributedObjects            number of non-internal distributed objects
     * @param mapCount                      number of maps
     * @param mapEntries                    owned entries of all maps
     * @param ownedEntryMemoryCost          owned entry memory of all maps in bytes
     * @param ownedEntryMemoryCostFormatted human readable owned memory
     * @param backupEntryMemoryCost         backup entry memory of all maps in bytes
     * @param backupEntryMemoryCostFormatted human readable backup memory
     */
    public record Totals(
            int distributedObjects,
            int mapCount,
            long mapEntries,
            long ownedEntryMemoryCost,
            String ownedEntryMemoryCostFormatted,
            long backupEntryMemoryCost,
            String backupEntryMemoryCostFormatted) {
    }

    /**
     * Memory footprint of one map.
     *
     * @param name                  map name
     * @param ownedEntryCount       owned entries
     * @param ownedEntryMemoryCost  owned memory in bytes
     * @param backupEntryMemoryCost backup memory in bytes
     */
    public record MapMemory(String name, long ownedEntryCount, long ownedEntryMemoryCost, long backupEntryMemoryCost) {
    }
}
