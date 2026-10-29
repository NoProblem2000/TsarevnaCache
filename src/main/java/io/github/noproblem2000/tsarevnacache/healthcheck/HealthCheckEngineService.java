package io.github.noproblem2000.tsarevnacache.healthcheck;

import com.hazelcast.cluster.Member;
import io.github.noproblem2000.tsarevnacache.api.dto.DeserializationErrorCode;
import io.github.noproblem2000.tsarevnacache.api.dto.EntryDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.EntryMetadataDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.EntryPageDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.InspectedValue;
import io.github.noproblem2000.tsarevnacache.api.dto.MapStatsDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.StructureDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.StructureType;
import io.github.noproblem2000.tsarevnacache.api.dto.StructuresDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.UnresolvedObjectDTO;
import io.github.noproblem2000.tsarevnacache.config.properties.HealthCheckProperties;
import io.github.noproblem2000.tsarevnacache.dto.DtoSchemaRegistryService;
import io.github.noproblem2000.tsarevnacache.hazelcast.ClientAccess;
import io.github.noproblem2000.tsarevnacache.healthcheck.ClusterSnapshot.MapSnapshot;
import io.github.noproblem2000.tsarevnacache.healthcheck.ClusterSnapshot.MissingDto;
import io.github.noproblem2000.tsarevnacache.healthcheck.ClusterSnapshot.QueueSnapshot;
import io.github.noproblem2000.tsarevnacache.healthcheck.ClusterSnapshot.Sample;
import io.github.noproblem2000.tsarevnacache.maps.EntryQuery;
import io.github.noproblem2000.tsarevnacache.maps.MapInspectorService;
import io.github.noproblem2000.tsarevnacache.metrics.ClusterMetricsService;
import io.github.noproblem2000.tsarevnacache.structures.StructureRegistryService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Automated healthcheck: collects member metrics, structure sizes and a sample of every large map,
 * evaluates {@link HealthCheckRules} and keeps the latest result. Runs every
 * {@code admin.healthcheck.interval-ms} (one minute by default) and on demand.
 */
@Slf4j
@Service
public class HealthCheckEngineService {

    private final ClientAccess clientAccess;
    private final StructureRegistryService structures;
    private final MapInspectorService maps;
    private final ClusterMetricsService metrics;
    private final DtoSchemaRegistryService dtoSchemas;
    private final HealthCheckIgnoreService ignores;
    private final HealthCheckProperties properties;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    private volatile Run latest;

    @Autowired
    public HealthCheckEngineService(ClientAccess clientAccess, StructureRegistryService structures,
                                    MapInspectorService maps, ClusterMetricsService metrics,
                                    DtoSchemaRegistryService dtoSchemas, HealthCheckIgnoreService ignores,
                                    HealthCheckProperties properties) {
        this(clientAccess, structures, maps, metrics, dtoSchemas, ignores, properties, Clock.systemUTC());
    }

    HealthCheckEngineService(ClientAccess clientAccess, StructureRegistryService structures, MapInspectorService maps,
                             ClusterMetricsService metrics, DtoSchemaRegistryService dtoSchemas,
                             HealthCheckIgnoreService ignores, HealthCheckProperties properties, Clock clock) {
        this.clientAccess = clientAccess;
        this.structures = structures;
        this.maps = maps;
        this.metrics = metrics;
        this.dtoSchemas = dtoSchemas;
        this.ignores = ignores;
        this.properties = properties;
        this.clock = clock;
    }

    /** Result of one run before the ignore list is applied. */
    public record Run(Instant time, long durationMillis, boolean clusterAvailable, String error,
                      List<HealthCheckIssue> issues, ClusterSnapshot snapshot, int checkedMaps, int sampledMaps) {
    }

    /** Background run every minute (see {@code admin.healthcheck.*}). */
    @Scheduled(fixedRateString = "${admin.healthcheck.interval-ms:60000}",
            initialDelayString = "${admin.healthcheck.initial-delay-ms:15000}")
    public void scheduledRun() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            runChecks();
        } catch (RuntimeException e) {
            log.warn("Scheduled healthcheck failed: {}", e.toString());
        }
    }

    /** Runs all checks now (waits for a run that is already in progress) and returns the report. */
    public HealthCheckReport runChecks() {
        running.lock();
        try {
            latest = execute(latest);
        } finally {
            running.unlock();
        }
        return report();
    }

    /** Latest report; the first call runs the checks. The ignore list is applied on every call. */
    public HealthCheckReport report() {
        Run run = latest;
        return run == null ? runChecks() : toReport(run);
    }

    /** Latest run, empty before the first one. */
    public Optional<Run> latestRun() {
        return Optional.ofNullable(latest);
    }

    // ------------------------------------------------------------------ run

    private Run execute(Run previous) {
        long started = System.nanoTime();
        Instant now = Instant.now(clock);
        if (!clientAccess.isConnected()) {
            return new Run(now, elapsed(started), false, "The Hazelcast cluster is not reachable", List.of(),
                    new ClusterSnapshot(0, 0, List.of(), List.of(), List.of()), 0, 0);
        }
        try {
            Collected collected = collect();
            Map<String, Instant> firstSeen = new HashMap<>();
            if (previous != null) {
                previous.issues().forEach(issue -> firstSeen.put(issue.issueId(), issue.detectedAt()));
            }
            List<HealthCheckIssue> issues = HealthCheckRules.evaluate(collected.snapshot(), properties.getThresholds(), now)
                    .stream()
                    .map(issue -> firstSeen.containsKey(issue.issueId()) ? issue.detectedAt(firstSeen.get(issue.issueId())) : issue)
                    .toList();
            long duration = elapsed(started);
            log.debug("Healthcheck: {} issue(s) in {} ms", issues.size(), duration);
            return new Run(now, duration, true, null, issues, collected.snapshot(), collected.snapshot().maps().size(),
                    collected.sampledMaps());
        } catch (RuntimeException e) {
            log.warn("Healthcheck run failed: {}", e.toString());
            return new Run(now, elapsed(started), false, e.getMessage() == null ? e.toString() : e.getMessage(),
                    List.of(), new ClusterSnapshot(0, 0, List.of(), List.of(), List.of()), 0, 0);
        }
    }

    private record Collected(ClusterSnapshot snapshot, int sampledMaps) {
    }

    private Collected collect() {
        metrics.invalidate();
        int dataMembers = 0;
        int liteMembers = 0;
        for (Member member : clientAccess.hazelcast().getCluster().getMembers()) {
            if (member.isLiteMember()) {
                liteMembers++;
            } else {
                dataMembers++;
            }
        }

        StructuresDTO registry = structures.list(false);
        List<MapSnapshot> mapSnapshots = new ArrayList<>();
        for (StructureDTO map : registry.groups().getOrDefault(StructureType.MAP, List.of())) {
            try {
                mapSnapshots.add(snapshotOf(maps.stats(map.name()), null));
            } catch (RuntimeException e) {
                log.debug("Healthcheck: no statistics for map {}: {}", map.name(), e.toString());
            }
        }

        // sample the largest maps: TTL / max idle of their entries and DTOs without a schema
        Map<String, MissingDtoBuilder> missing = new LinkedHashMap<>();
        List<MapSnapshot> bySize = mapSnapshots.stream()
                .filter(map -> map.ownedEntryCount() > 0)
                .sorted(Comparator.comparingLong(MapSnapshot::ownedMemory).reversed()
                        .thenComparing(Comparator.comparingLong(MapSnapshot::ownedEntryCount).reversed()))
                .limit(properties.getMaxSampledMaps())
                .toList();
        Map<String, Sample> samples = new HashMap<>();
        for (MapSnapshot map : bySize) {
            try {
                EntryPageDTO page = maps.entries(map.name(),
                        new EntryQuery(0, properties.getSampleSize(), null, null, null, null, null, null));
                samples.put(map.name(), sample(page.entries()));
                for (EntryDTO entry : page.entries()) {
                    collectMissing(entry.key(), map.name(), missing);
                    collectMissing(entry.value(), map.name(), missing);
                }
            } catch (RuntimeException e) {
                log.debug("Healthcheck: cannot sample map {}: {}", map.name(), e.toString());
            }
        }
        List<MapSnapshot> withSamples = mapSnapshots.stream()
                .map(map -> samples.containsKey(map.name()) ? withSample(map, samples.get(map.name())) : map)
                .toList();

        List<QueueSnapshot> queues = registry.groups().getOrDefault(StructureType.QUEUE, List.of()).stream()
                .filter(queue -> queue.size() != null)
                .map(queue -> new QueueSnapshot(queue.name(), queue.size()))
                .toList();

        List<MissingDto> missingDtos = missing.values().stream().map(MissingDtoBuilder::build).toList();
        return new Collected(new ClusterSnapshot(dataMembers, liteMembers, withSamples, queues, missingDtos), samples.size());
    }

    private static MapSnapshot snapshotOf(MapStatsDTO stats, Sample sample) {
        return new MapSnapshot(stats.name(), stats.ownedEntryCount(), stats.backupEntryCount(),
                stats.ownedEntryMemoryCost(), stats.backupEntryMemoryCost(), stats.hits(),
                stats.getOperationCount(), stats.putOperationCount(), stats.setOperationCount(),
                stats.totalGetLatency(), stats.totalPutLatency(), sample);
    }

    private static MapSnapshot withSample(MapSnapshot map, Sample sample) {
        return new MapSnapshot(map.name(), map.ownedEntryCount(), map.backupEntryCount(), map.ownedMemory(),
                map.backupMemory(), map.hits(), map.getCount(), map.putCount(), map.setCount(), map.totalGetLatency(),
                map.totalPutLatency(), sample);
    }

    /** Entries with entry view metadata; those without TTL and max idle are stored forever. */
    static Sample sample(List<EntryDTO> entries) {
        int known = 0;
        int forever = 0;
        for (EntryDTO entry : entries) {
            EntryMetadataDTO metadata = entry.metadata();
            if (metadata == null) {
                continue;
            }
            known++;
            if (metadata.ttl() == null && metadata.maxIdle() == null) {
                forever++;
            }
        }
        return new Sample(known, forever);
    }

    private void collectMissing(InspectedValue value, String mapName, Map<String, MissingDtoBuilder> missing) {
        UnresolvedObjectDTO unresolved = value == null ? null : value.unresolved();
        if (unresolved == null || unresolved.metadata() == null) {
            return;
        }
        Map<String, Object> metadata = unresolved.metadata();
        Integer factoryId;
        Integer classId;
        boolean fixable;
        if (unresolved.errorCode() == DeserializationErrorCode.DTO_SCHEMA_MISSING) {
            factoryId = integer(metadata.get("missingFactoryId"));
            classId = integer(metadata.get("missingClassId"));
            fixable = true;
        } else if (unresolved.errorCode() == DeserializationErrorCode.DATA_SERIALIZABLE_FACTORY_MISSING
                && Boolean.TRUE.equals(metadata.get("identified"))) {
            factoryId = integer(metadata.get("factoryId"));
            classId = integer(metadata.get("classId"));
            fixable = false;
        } else {
            return;
        }
        if (factoryId == null || classId == null) {
            return;
        }
        String className = dtoSchemas.classNameFor(factoryId, classId).orElse(null);
        missing.computeIfAbsent(factoryId + ":" + classId, key -> new MissingDtoBuilder(factoryId, classId, className, fixable))
                .add(mapName);
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static final class MissingDtoBuilder {
        private final int factoryId;
        private final int classId;
        private final String className;
        private final boolean fixable;
        private final Set<String> maps = new TreeSet<>();
        private int occurrences;

        MissingDtoBuilder(int factoryId, int classId, String className, boolean fixable) {
            this.factoryId = factoryId;
            this.classId = classId;
            this.className = className;
            this.fixable = fixable;
        }

        void add(String map) {
            maps.add(map);
            occurrences++;
        }

        MissingDto build() {
            return new MissingDto(factoryId, classId, className, fixable, maps, occurrences);
        }
    }

    // ------------------------------------------------------------------ report

    private HealthCheckReport toReport(Run run) {
        List<HealthCheckIssue> active = new ArrayList<>();
        List<HealthCheckIssue> hidden = new ArrayList<>();
        for (HealthCheckIssue issue : run.issues()) {
            (ignores.find(issue).isPresent() ? hidden : active).add(issue);
        }
        int score = run.clusterAvailable() ? HealthCheckRules.score(active) : 0;
        HealthStatus status = run.clusterAvailable() ? HealthCheckRules.status(active) : HealthStatus.CRITICAL;
        HealthCheckReport.Counts counts = new HealthCheckReport.Counts(
                count(active, HealthSeverity.CRITICAL), count(active, HealthSeverity.WARNING),
                count(active, HealthSeverity.INFO), hidden.size());
        return new HealthCheckReport(score, status, run.time(), run.durationMillis(), run.clusterAvailable(), run.error(),
                counts, List.copyOf(active), List.copyOf(hidden), ignores.list(), run.checkedMaps(), run.sampledMaps(),
                properties.isEnabled() ? properties.getIntervalMs() : 0);
    }

    private static int count(List<HealthCheckIssue> issues, HealthSeverity severity) {
        return (int) issues.stream().filter(issue -> issue.severity() == severity).count();
    }

    private static long elapsed(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
