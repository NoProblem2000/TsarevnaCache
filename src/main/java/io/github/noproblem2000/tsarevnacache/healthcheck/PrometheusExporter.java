package io.github.noproblem2000.tsarevnacache.healthcheck;

import io.github.noproblem2000.tsarevnacache.healthcheck.ClusterSnapshot.MapSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Renders the latest healthcheck in the Prometheus text exposition format 0.0.4.
 */
public final class PrometheusExporter {

    public static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    private PrometheusExporter() {
    }

    /**
     * @param report health score and issue counts
     * @param maps   map statistics of the same run (empty before the first run)
     */
    public static String format(HealthCheckReport report, List<MapSnapshot> maps) {
        StringBuilder out = new StringBuilder(512 + maps.size() * 200);
        gauge(out, "hazelcast_inspector_health_score", "Health score of the cluster (0..100).");
        out.append("hazelcast_inspector_health_score ").append(report.healthScore()).append('\n');

        gauge(out, "hazelcast_inspector_health_issues_total", "Active healthcheck issues by severity (ignored issues excluded).");
        out.append("hazelcast_inspector_health_issues_total{severity=\"CRITICAL\"} ").append(report.counts().critical()).append('\n');
        out.append("hazelcast_inspector_health_issues_total{severity=\"WARNING\"} ").append(report.counts().warning()).append('\n');
        out.append("hazelcast_inspector_health_issues_total{severity=\"INFO\"} ").append(report.counts().info()).append('\n');

        gauge(out, "hazelcast_inspector_health_ignored_issues_total", "Issues hidden by the ignore list.");
        out.append("hazelcast_inspector_health_ignored_issues_total ").append(report.counts().ignored()).append('\n');

        gauge(out, "hazelcast_inspector_cluster_up", "1 when the last healthcheck could reach the cluster.");
        out.append("hazelcast_inspector_cluster_up ").append(report.clusterAvailable() ? 1 : 0).append('\n');

        if (report.lastRunTime() != null) {
            gauge(out, "hazelcast_inspector_health_last_run_timestamp_seconds", "End of the last healthcheck run (Unix time).");
            out.append("hazelcast_inspector_health_last_run_timestamp_seconds ")
                    .append(String.format(Locale.ROOT, "%.3f", report.lastRunTime().toEpochMilli() / 1000.0)).append('\n');
        }

        List<MapSnapshot> sorted = maps.stream().sorted(Comparator.comparing(MapSnapshot::name)).toList();
        gauge(out, "hazelcast_inspector_map_entries_total", "Owned entries of an IMap over all members.");
        for (MapSnapshot map : sorted) {
            out.append("hazelcast_inspector_map_entries_total{map=\"").append(label(map.name())).append("\"} ")
                    .append(map.ownedEntryCount()).append('\n');
        }
        gauge(out, "hazelcast_inspector_map_memory_bytes", "Entry memory of an IMap over all members.");
        for (MapSnapshot map : sorted) {
            String name = label(map.name());
            out.append("hazelcast_inspector_map_memory_bytes{map=\"").append(name).append("\",type=\"owned\"} ")
                    .append(map.ownedMemory()).append('\n');
            out.append("hazelcast_inspector_map_memory_bytes{map=\"").append(name).append("\",type=\"backup\"} ")
                    .append(map.backupMemory()).append('\n');
        }
        return out.toString();
    }

    private static void gauge(StringBuilder out, String name, String help) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n');
        out.append("# TYPE ").append(name).append(" gauge\n");
    }

    /** Label value escaping of the text format: backslash, double quote and line feed. */
    static String label(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
