package io.github.noproblem2000.tsarevnacache.healthcheck;

import java.util.List;
import java.util.Set;

/**
 * Everything the rules look at, collected once per run by {@link HealthCheckEngineService}.
 *
 * @param dataMembers data (non-lite) members of the cluster
 * @param liteMembers lite members
 * @param maps        statistics (and samples) of the user maps
 * @param queues      sizes of the user queues
 * @param missingDtos IdentifiedDataSerializable classes without a schema found while sampling
 */
public record ClusterSnapshot(
        int dataMembers,
        int liteMembers,
        List<MapSnapshot> maps,
        List<QueueSnapshot> queues,
        List<MissingDto> missingDtos) {

    /**
     * Aggregated member metrics of one map. Operation counters and latency sums (milliseconds) are
     * totals since the members started.
     *
     * @param sample TTL / max idle of sampled entries, {@code null} when the map was not sampled
     */
    public record MapSnapshot(
            String name,
            long ownedEntryCount,
            long backupEntryCount,
            long ownedMemory,
            long backupMemory,
            long hits,
            long getCount,
            long putCount,
            long setCount,
            long totalGetLatency,
            long totalPutLatency,
            Sample sample) {
    }

    /**
     * @param entries       sampled entries with entry view metadata
     * @param withoutExpiry of those, entries with neither a TTL nor a max idle (stored forever)
     */
    public record Sample(int entries, int withoutExpiry) {
    }

    public record QueueSnapshot(String name, long size) {
    }

    /**
     * @param className   class name known to the factory mapping, may be {@code null}
     * @param fixable     the factory id is served by the DTO Schema Studio, so a schema helps at once
     *                    (otherwise the service must be restarted after adding it)
     * @param maps        maps the values were found in
     * @param occurrences number of sampled keys / values of the class
     */
    public record MissingDto(int factoryId, int classId, String className, boolean fixable, Set<String> maps,
                             int occurrences) {
    }
}
