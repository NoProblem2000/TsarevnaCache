package io.github.noproblem2000.tsarevnacache.healthcheck;

import io.github.noproblem2000.tsarevnacache.api.dto.ByteSize;
import io.github.noproblem2000.tsarevnacache.config.properties.HealthCheckProperties.Thresholds;
import io.github.noproblem2000.tsarevnacache.healthcheck.ClusterSnapshot.MapSnapshot;
import io.github.noproblem2000.tsarevnacache.healthcheck.ClusterSnapshot.MissingDto;
import io.github.noproblem2000.tsarevnacache.healthcheck.ClusterSnapshot.QueueSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The seven best practice rules, evaluated over a {@link ClusterSnapshot}. Pure functions: no
 * cluster access, so every rule is unit tested in isolation.
 */
public final class HealthCheckRules {

    /** {@code targetName} of cluster wide issues. */
    public static final String CLUSTER = "Cluster";

    private static final int CRITICAL_PENALTY = 25;
    private static final int WARNING_PENALTY = 10;

    private static final Comparator<HealthCheckIssue> ORDER = Comparator
            .comparing(HealthCheckIssue::severity)
            .thenComparing(HealthCheckIssue::checkType)
            .thenComparing(HealthCheckIssue::targetName, String.CASE_INSENSITIVE_ORDER);

    private HealthCheckRules() {
    }

    /** All issues of a snapshot, most severe first. */
    public static List<HealthCheckIssue> evaluate(ClusterSnapshot snapshot, Thresholds thresholds, Instant now) {
        List<HealthCheckIssue> issues = new ArrayList<>();
        singleMember(snapshot, now, issues);
        for (MapSnapshot map : snapshot.maps()) {
            unboundedNoTtl(map, thresholds, now, issues);
            zombieWriteOnly(map, thresholds, now, issues);
            backupDisabled(map, snapshot.dataMembers(), now, issues);
            highLatency(map, thresholds, now, issues);
        }
        for (QueueSnapshot queue : snapshot.queues()) {
            queueBacklog(queue, thresholds, now, issues);
        }
        for (MissingDto dto : snapshot.missingDtos()) {
            unregisteredSchema(dto, now, issues);
        }
        issues.sort(ORDER);
        return issues;
    }

    /** 100 minus 25 per active critical and 10 per active warning issue, never below 0. */
    public static int score(List<HealthCheckIssue> activeIssues) {
        long critical = activeIssues.stream().filter(issue -> issue.severity() == HealthSeverity.CRITICAL).count();
        long warning = activeIssues.stream().filter(issue -> issue.severity() == HealthSeverity.WARNING).count();
        return (int) Math.max(0, 100 - critical * CRITICAL_PENALTY - warning * WARNING_PENALTY);
    }

    public static HealthStatus status(List<HealthCheckIssue> activeIssues) {
        if (activeIssues.stream().anyMatch(issue -> issue.severity() == HealthSeverity.CRITICAL)) {
            return HealthStatus.CRITICAL;
        }
        if (activeIssues.stream().anyMatch(issue -> issue.severity() == HealthSeverity.WARNING)) {
            return HealthStatus.DEGRADED;
        }
        return HealthStatus.HEALTHY;
    }

    // ------------------------------------------------------------------ rules

    /** 1. A single data member: no fault tolerance, partition backups have nowhere to live. */
    static void singleMember(ClusterSnapshot snapshot, Instant now, List<HealthCheckIssue> issues) {
        if (snapshot.dataMembers() != 1) {
            return;
        }
        issues.add(issue(HealthCheckType.CLUSTER_SINGLE_MEMBER, HealthSeverity.WARNING, CLUSTER,
                "dataMembers=1, liteMembers=" + snapshot.liteMembers(), now,
                details("dataMembers", 1, "liteMembers", snapshot.liteMembers())));
    }

    /** 2. Large map whose sampled entries are stored forever. */
    static void unboundedNoTtl(MapSnapshot map, Thresholds thresholds, Instant now, List<HealthCheckIssue> issues) {
        ClusterSnapshot.Sample sample = map.sample();
        boolean large = map.ownedEntryCount() > thresholds.getUnboundedMinEntries()
                || map.ownedMemory() > thresholds.getUnboundedWarningMemory().toBytes();
        if (!large || sample == null || sample.entries() == 0 || sample.withoutExpiry() < sample.entries()) {
            return;
        }
        HealthSeverity severity = map.ownedMemory() > thresholds.getUnboundedCriticalMemory().toBytes()
                ? HealthSeverity.CRITICAL : HealthSeverity.WARNING;
        issues.add(issue(HealthCheckType.MAP_UNBOUNDED_NO_TTL, severity, map.name(),
                "entries=" + map.ownedEntryCount() + ", memory=" + ByteSize.format(map.ownedMemory())
                        + ", ttl=infinite, maxIdle=infinite (" + sample.withoutExpiry() + " of " + sample.entries()
                        + " sampled entries)",
                now,
                details("entries", map.ownedEntryCount(), "memoryBytes", map.ownedMemory(),
                        "sampled", sample.entries(), "withoutExpiry", sample.withoutExpiry())));
    }

    /** 3. Written but never read ("dead cache"). */
    static void zombieWriteOnly(MapSnapshot map, Thresholds thresholds, Instant now, List<HealthCheckIssue> issues) {
        long writes = map.putCount() + map.setCount();
        if (map.ownedEntryCount() <= thresholds.getZombieMinEntries() || writes == 0
                || map.getCount() != 0 || map.hits() != 0) {
            return;
        }
        issues.add(issue(HealthCheckType.MAP_ZOMBIE_WRITE_ONLY, HealthSeverity.WARNING, map.name(),
                "entries=" + map.ownedEntryCount() + ", puts=" + map.putCount() + ", sets=" + map.setCount()
                        + ", gets=0, hits=0",
                now,
                details("entries", map.ownedEntryCount(), "puts", map.putCount(), "sets", map.setCount(),
                        "memoryBytes", map.ownedMemory())));
    }

    /** 4. Several members but no backup entries: a member failure loses data. */
    static void backupDisabled(MapSnapshot map, int dataMembers, Instant now, List<HealthCheckIssue> issues) {
        if (dataMembers < 2 || map.ownedEntryCount() == 0 || map.backupEntryCount() != 0) {
            return;
        }
        issues.add(issue(HealthCheckType.MAP_BACKUP_DISABLED_OR_BROKEN, HealthSeverity.CRITICAL, map.name(),
                "dataMembers=" + dataMembers + ", ownedEntries=" + map.ownedEntryCount() + ", backupEntries=0",
                now,
                details("dataMembers", dataMembers, "entries", map.ownedEntryCount(), "memoryBytes", map.ownedMemory())));
    }

    /** 5. Average get or put latency above the threshold. */
    static void highLatency(MapSnapshot map, Thresholds thresholds, Instant now, List<HealthCheckIssue> issues) {
        Double get = average(map.totalGetLatency(), map.getCount(), thresholds.getLatencyMinOperations());
        Double put = average(map.totalPutLatency(), map.putCount(), thresholds.getLatencyMinOperations());
        long limit = thresholds.getLatencyMs();
        boolean slowGet = get != null && get > limit;
        boolean slowPut = put != null && put > limit;
        if (!slowGet && !slowPut) {
            return;
        }
        Map<String, Object> details = details("thresholdMs", limit, "gets", map.getCount(), "puts", map.putCount());
        if (get != null) {
            details.put("avgGetMs", round(get));
        }
        if (put != null) {
            details.put("avgPutMs", round(put));
        }
        issues.add(issue(HealthCheckType.MAP_HIGH_LATENCY, HealthSeverity.WARNING, map.name(),
                "avgGet=" + millis(get) + " (gets=" + map.getCount() + "), avgPut=" + millis(put)
                        + " (puts=" + map.putCount() + "), threshold=" + limit + " ms",
                now, details));
    }

    /** 6. Queue backlog: consumers are too slow or stuck. */
    static void queueBacklog(QueueSnapshot queue, Thresholds thresholds, Instant now, List<HealthCheckIssue> issues) {
        if (queue.size() <= thresholds.getQueueBacklog()) {
            return;
        }
        issues.add(issue(HealthCheckType.QUEUE_BACKLOG_GROWTH, HealthSeverity.WARNING, queue.name(),
                "size=" + queue.size() + ", threshold=" + thresholds.getQueueBacklog(), now,
                details("size", queue.size(), "threshold", thresholds.getQueueBacklog())));
    }

    /** 7. Sampled IdentifiedDataSerializable values without a DTO schema. */
    static void unregisteredSchema(MissingDto dto, Instant now, List<HealthCheckIssue> issues) {
        String ids = "factoryId=" + dto.factoryId() + ", classId=" + dto.classId();
        String target = dto.className() != null ? dto.className() : ids;
        Map<String, Object> details = details("factoryId", dto.factoryId(), "classId", dto.classId(),
                "maps", List.copyOf(dto.maps()), "occurrences", dto.occurrences());
        if (dto.className() != null) {
            details.put("className", dto.className());
        }
        HealthCheckType type = HealthCheckType.DTO_UNREGISTERED_SCHEMA;
        issues.add(new HealthCheckIssue(
                type.issueId(dto.factoryId() + ":" + dto.classId()),
                type,
                dto.fixable() ? HealthSeverity.WARNING : HealthSeverity.INFO,
                type.targetType(),
                target,
                ids + ", maps=" + String.join(", ", dto.maps()) + " (" + dto.occurrences() + (dto.occurrences() == 1 ? " sampled value)" : " sampled values)"),
                dto.fixable() ? type.name() : type.name() + "_RESTART",
                now,
                details));
    }

    // ------------------------------------------------------------------ helpers

    private static HealthCheckIssue issue(HealthCheckType type, HealthSeverity severity, String target, String current,
                                          Instant now, Map<String, Object> details) {
        String recommendation = type == HealthCheckType.MAP_UNBOUNDED_NO_TTL && severity == HealthSeverity.CRITICAL
                ? type.name() + "_CRITICAL" : type.name();
        return new HealthCheckIssue(type.issueId(target), type, severity, type.targetType(), target, current,
                recommendation, now, details);
    }

    private static Map<String, Object> details(Object... keyValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            details.put((String) keyValues[i], keyValues[i + 1]);
        }
        return details;
    }

    private static Double average(long total, long operations, long minOperations) {
        return operations < minOperations ? null : (double) total / operations;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static String millis(Double value) {
        return value == null ? "n/a" : String.format(Locale.ROOT, "%.1f ms", value);
    }
}
