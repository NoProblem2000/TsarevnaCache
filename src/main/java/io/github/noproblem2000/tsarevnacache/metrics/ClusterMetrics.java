package io.github.noproblem2000.tsarevnacache.metrics;

import io.github.noproblem2000.tsarevnacache.api.dto.StatsStatusDTO;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Metrics of all members that answered, plus the problems encountered for the others.
 *
 * @param members metrics per member
 * @param errors  human readable per-member problems
 */
public record ClusterMetrics(List<MemberMetrics> members, List<String> errors) {

    public static final String MAP = "map";
    public static final String MAP_NEAR_CACHE = "map.nearcache";
    public static final String MULTI_MAP = "multiMap";
    public static final String REPLICATED_MAP = "replicatedMap";
    public static final String QUEUE = "queue";

    public boolean available() {
        return !members.isEmpty();
    }

    /** Whether any member reported metrics for the object. */
    public boolean has(String prefix, String objectName) {
        return members.stream().anyMatch(member -> !member.of(prefix, objectName).isEmpty());
    }

    /** Sum of a metric over all members. */
    public long sum(String prefix, String objectName, String metric) {
        return members.stream()
                .map(member -> member.of(prefix, objectName).get(metric))
                .filter(value -> value != null)
                .mapToLong(Number::longValue)
                .sum();
    }

    /** Maximum of a metric over all members, {@code null} if absent or not positive. */
    public Long max(String prefix, String objectName, String metric) {
        return members.stream()
                .map(member -> member.of(prefix, objectName).get(metric))
                .filter(value -> value != null && value.longValue() > 0)
                .map(Number::longValue)
                .max(Long::compare)
                .orElse(null);
    }

    /** Minimum positive value of a metric over all members. */
    public Long minPositive(String prefix, String objectName, String metric) {
        return members.stream()
                .map(member -> member.of(prefix, objectName).get(metric))
                .filter(value -> value != null && value.longValue() > 0)
                .map(Number::longValue)
                .min(Long::compare)
                .orElse(null);
    }

    /** Names of all objects reported under a prefix. */
    public Set<String> objectNames(String prefix) {
        Set<String> names = new TreeSet<>();
        members.forEach(member -> names.addAll(member.objects().getOrDefault(prefix, Map.of()).keySet()));
        return names;
    }

    public Optional<MemberMetrics> member(UUID uuid) {
        return members.stream().filter(member -> member.uuid().equals(uuid)).findFirst();
    }

    public StatsStatusDTO status() {
        Long newest = members.stream().map(MemberMetrics::collectedAt).max(Long::compare).orElse(null);
        return new StatsStatusDTO(available(), newest, members.size(), errors);
    }
}
