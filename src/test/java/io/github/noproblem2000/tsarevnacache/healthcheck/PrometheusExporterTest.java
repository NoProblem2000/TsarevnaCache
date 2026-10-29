package io.github.noproblem2000.tsarevnacache.healthcheck;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.noproblem2000.tsarevnacache.healthcheck.ClusterSnapshot.MapSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrometheusExporterTest {

    @Test
    void scoreIssuesAndMapsAreExported() {
        HealthCheckReport report = new HealthCheckReport(65, HealthStatus.CRITICAL, Instant.ofEpochSecond(1_800_000_000),
                12, true, null, new HealthCheckReport.Counts(1, 2, 3, 4), List.of(), List.of(), List.of(), 2, 2, 60_000);
        List<MapSnapshot> maps = List.of(
                new MapSnapshot("users", 10, 10, 2048, 1024, 0, 0, 0, 0, 0, 0, null),
                new MapSnapshot("we\"ird\\map\nname", 1, 0, 64, 0, 0, 0, 0, 0, 0, 0, null));

        String text = PrometheusExporter.format(report, maps);

        assertThat(text).contains(
                "# TYPE hazelcast_inspector_health_score gauge\nhazelcast_inspector_health_score 65\n",
                "hazelcast_inspector_health_issues_total{severity=\"CRITICAL\"} 1\n",
                "hazelcast_inspector_health_issues_total{severity=\"WARNING\"} 2\n",
                "hazelcast_inspector_health_issues_total{severity=\"INFO\"} 3\n",
                "hazelcast_inspector_health_ignored_issues_total 4\n",
                "hazelcast_inspector_cluster_up 1\n",
                "hazelcast_inspector_health_last_run_timestamp_seconds 1800000000.000\n",
                "hazelcast_inspector_map_entries_total{map=\"users\"} 10\n",
                "hazelcast_inspector_map_memory_bytes{map=\"users\",type=\"owned\"} 2048\n",
                "hazelcast_inspector_map_memory_bytes{map=\"users\",type=\"backup\"} 1024\n",
                "hazelcast_inspector_map_entries_total{map=\"we\\\"ird\\\\map\\nname\"} 1\n");
        assertThat(text).endsWith("\n");
        // every sample line is "name{labels} value" or "name value"
        for (String line : text.split("\n")) {
            assertThat(line).matches("# (HELP|TYPE) .+|[a-z_]+(\\{.+})? [0-9.]+");
        }
    }
}
