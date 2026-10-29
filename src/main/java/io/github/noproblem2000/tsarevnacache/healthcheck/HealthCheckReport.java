package io.github.noproblem2000.tsarevnacache.healthcheck;

import java.time.Instant;
import java.util.List;

/**
 * Result of the latest health check run.
 *
 * @param healthScore      0..100; 100 means no active critical or warning issue
 * @param status           {@code HEALTHY}, {@code DEGRADED} or {@code CRITICAL}
 * @param lastRunTime      end of the run, {@code null} before the first run
 * @param durationMillis   duration of the run
 * @param clusterAvailable {@code false} when the cluster could not be reached (score 0)
 * @param error            reason when the run failed
 * @param counts           active issues per severity plus ignored issues
 * @param activeIssues     issues that count, most severe first
 * @param ignoredIssues    issues hidden by the ignore list
 * @param ignores          entries of the ignore list
 * @param checkedMaps      number of maps analyzed
 * @param sampledMaps      number of maps whose entries were sampled
 * @param intervalMillis   period of the scheduled runs, {@code 0} when scheduling is disabled
 */
public record HealthCheckReport(
        int healthScore,
        HealthStatus status,
        Instant lastRunTime,
        long durationMillis,
        boolean clusterAvailable,
        String error,
        Counts counts,
        List<HealthCheckIssue> activeIssues,
        List<HealthCheckIssue> ignoredIssues,
        List<HealthCheckIgnore> ignores,
        int checkedMaps,
        int sampledMaps,
        long intervalMillis) {

    /** Number of active issues per severity and of ignored issues. */
    public record Counts(int critical, int warning, int info, int ignored) {
    }
}
