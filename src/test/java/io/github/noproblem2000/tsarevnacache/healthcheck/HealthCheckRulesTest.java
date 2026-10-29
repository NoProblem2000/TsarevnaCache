package io.github.noproblem2000.tsarevnacache.healthcheck;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.noproblem2000.tsarevnacache.config.properties.HealthCheckProperties.Thresholds;
import io.github.noproblem2000.tsarevnacache.healthcheck.ClusterSnapshot.MapSnapshot;
import io.github.noproblem2000.tsarevnacache.healthcheck.ClusterSnapshot.MissingDto;
import io.github.noproblem2000.tsarevnacache.healthcheck.ClusterSnapshot.QueueSnapshot;
import io.github.noproblem2000.tsarevnacache.healthcheck.ClusterSnapshot.Sample;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HealthCheckRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final long MB = 1024 * 1024;
    private final Thresholds thresholds = new Thresholds();

    private static MapSnapshot map(String name, long entries, long backups, long memory, long hits, long gets, long puts,
                                   long getLatency, long putLatency, Sample sample) {
        return new MapSnapshot(name, entries, backups, memory, memory / 2, hits, gets, puts, 0, getLatency, putLatency, sample);
    }

    private List<HealthCheckIssue> evaluate(int members, List<MapSnapshot> maps, List<QueueSnapshot> queues,
                                            List<MissingDto> dtos) {
        return HealthCheckRules.evaluate(new ClusterSnapshot(members, 0, maps, queues, dtos), thresholds, NOW);
    }

    private static List<String> ids(List<HealthCheckIssue> issues) {
        return issues.stream().map(HealthCheckIssue::issueId).toList();
    }

    @Test
    void singleMemberIsAWarning() {
        List<HealthCheckIssue> issues = evaluate(1, List.of(), List.of(), List.of());
        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.issueId()).isEqualTo("CLUSTER_SINGLE_MEMBER:Cluster");
            assertThat(issue.severity()).isEqualTo(HealthSeverity.WARNING);
            assertThat(issue.targetType()).isEqualTo(HealthTargetType.CLUSTER);
            assertThat(issue.recommendationCode()).isEqualTo("CLUSTER_SINGLE_MEMBER");
            assertThat(issue.detectedAt()).isEqualTo(NOW);
        });
        assertThat(evaluate(3, List.of(), List.of(), List.of())).isEmpty();
    }

    @Test
    void unboundedMapsWithoutTtlAreReportedByEntriesOrMemory() {
        Sample forever = new Sample(20, 20);
        List<HealthCheckIssue> issues = evaluate(3, List.of(
                map("by-entries", 1_001, 1_001, MB, 1, 1, 1, 0, 0, forever),
                map("by-memory", 10, 10, 60 * MB, 1, 1, 1, 0, 0, forever),
                map("critical", 10, 10, 300 * MB, 1, 1, 1, 0, 0, forever),
                map("small", 1_000, 1_000, MB, 1, 1, 1, 0, 0, forever),
                map("with-ttl", 5_000, 5_000, MB, 1, 1, 1, 0, 0, new Sample(20, 19)),
                map("not-sampled", 5_000, 5_000, MB, 1, 1, 1, 0, 0, null)), List.of(), List.of());

        assertThat(ids(issues)).containsExactly("MAP_UNBOUNDED_NO_TTL:critical", "MAP_UNBOUNDED_NO_TTL:by-entries",
                "MAP_UNBOUNDED_NO_TTL:by-memory");
        HealthCheckIssue critical = issues.get(0);
        assertThat(critical.severity()).isEqualTo(HealthSeverity.CRITICAL);
        assertThat(critical.recommendationCode()).isEqualTo("MAP_UNBOUNDED_NO_TTL_CRITICAL");
        assertThat(critical.currentValue()).contains("memory=300", "ttl=infinite", "20 of 20 sampled");
        assertThat(issues.get(1).severity()).isEqualTo(HealthSeverity.WARNING);
    }

    @Test
    void writeOnlyMapsAreZombies() {
        List<HealthCheckIssue> issues = evaluate(3, List.of(
                map("zombie", 101, 101, MB, 0, 0, 500, 0, 0, null),
                map("read", 101, 101, MB, 0, 1, 500, 0, 0, null),
                map("hit", 101, 101, MB, 1, 0, 500, 0, 0, null),
                map("tiny", 100, 100, MB, 0, 0, 500, 0, 0, null),
                map("loaded", 500, 500, MB, 0, 0, 0, 0, 0, null)), List.of(), List.of());
        assertThat(ids(issues)).containsExactly("MAP_ZOMBIE_WRITE_ONLY:zombie");
        assertThat(issues.get(0).currentValue()).isEqualTo("entries=101, puts=500, sets=0, gets=0, hits=0");
    }

    @Test
    void missingBackupsAreCriticalOnlyWithSeveralMembers() {
        MapSnapshot noBackup = map("orders", 10, 0, MB, 1, 1, 1, 0, 0, null);
        assertThat(ids(evaluate(2, List.of(noBackup), List.of(), List.of())))
                .containsExactly("MAP_BACKUP_DISABLED_OR_BROKEN:orders");
        assertThat(evaluate(2, List.of(noBackup), List.of(), List.of()).get(0).severity()).isEqualTo(HealthSeverity.CRITICAL);
        assertThat(ids(evaluate(1, List.of(noBackup), List.of(), List.of()))).containsExactly("CLUSTER_SINGLE_MEMBER:Cluster");
        assertThat(evaluate(2, List.of(map("empty", 0, 0, 0, 0, 0, 0, 0, 0, null)), List.of(), List.of())).isEmpty();
    }

    @Test
    void averageLatencyAboveTheThresholdIsReported() {
        List<HealthCheckIssue> issues = evaluate(3, List.of(
                map("slow-get", 10, 10, MB, 100, 100, 100, 3_000, 100, null),
                map("slow-put", 10, 10, MB, 100, 100, 100, 100, 2_600, null),
                map("fast", 10, 10, MB, 100, 100, 100, 2_500, 2_500, null),
                map("few-ops", 10, 10, MB, 10, 10, 10, 10_000, 10_000, null)), List.of(), List.of());
        assertThat(ids(issues)).containsExactly("MAP_HIGH_LATENCY:slow-get", "MAP_HIGH_LATENCY:slow-put");
        assertThat(issues.get(0).currentValue()).isEqualTo("avgGet=30.0 ms (gets=100), avgPut=1.0 ms (puts=100), threshold=25 ms");
        assertThat(issues.get(0).details()).containsEntry("avgGetMs", 30.0);
    }

    @Test
    void queueBacklogAboveTheThresholdIsReported() {
        List<HealthCheckIssue> issues = evaluate(3, List.of(),
                List.of(new QueueSnapshot("jobs", 5_001), new QueueSnapshot("ok", 5_000)), List.of());
        assertThat(ids(issues)).containsExactly("QUEUE_BACKLOG_GROWTH:jobs");
        assertThat(issues.get(0).targetType()).isEqualTo(HealthTargetType.IQUEUE);
    }

    @Test
    void unregisteredDtosAreWarningsWhenASchemaHelpsAtOnce() {
        List<HealthCheckIssue> issues = evaluate(3, List.of(), List.of(), List.of(
                new MissingDto(1, 3, "UnknownDTO", true, Set.of("orders"), 4),
                new MissingDto(1000, 5, null, false, Set.of("legacy"), 1)));
        assertThat(ids(issues)).containsExactly("DTO_UNREGISTERED_SCHEMA:1:3", "DTO_UNREGISTERED_SCHEMA:1000:5");
        assertThat(issues.get(0).severity()).isEqualTo(HealthSeverity.WARNING);
        assertThat(issues.get(0).targetName()).isEqualTo("UnknownDTO");
        assertThat(issues.get(0).details()).containsEntry("classId", 3).containsEntry("maps", List.of("orders"));
        assertThat(issues.get(1).severity()).isEqualTo(HealthSeverity.INFO);
        assertThat(issues.get(1).targetName()).isEqualTo("factoryId=1000, classId=5");
        assertThat(issues.get(1).recommendationCode()).isEqualTo("DTO_UNREGISTERED_SCHEMA_RESTART");
    }

    @Test
    void scoreAndStatusIgnoreInfoIssues() {
        HealthCheckIssue critical = HealthCheckRules.evaluate(new ClusterSnapshot(2, 0,
                List.of(map("orders", 10, 0, MB, 1, 1, 1, 0, 0, null)), List.of(), List.of()), thresholds, NOW).get(0);
        HealthCheckIssue warning = evaluate(1, List.of(), List.of(), List.of()).get(0);
        HealthCheckIssue info = evaluate(3, List.of(), List.of(),
                List.of(new MissingDto(9, 9, null, false, Set.of("m"), 1))).get(0);

        assertThat(HealthCheckRules.score(List.of())).isEqualTo(100);
        assertThat(HealthCheckRules.score(List.of(info))).isEqualTo(100);
        assertThat(HealthCheckRules.score(List.of(warning, info))).isEqualTo(90);
        assertThat(HealthCheckRules.score(List.of(critical, warning))).isEqualTo(65);
        assertThat(HealthCheckRules.score(List.of(critical, critical, critical, critical, critical))).isZero();
        assertThat(HealthCheckRules.status(List.of(info))).isEqualTo(HealthStatus.HEALTHY);
        assertThat(HealthCheckRules.status(List.of(warning, info))).isEqualTo(HealthStatus.DEGRADED);
        assertThat(HealthCheckRules.status(List.of(critical, warning))).isEqualTo(HealthStatus.CRITICAL);
    }
}
