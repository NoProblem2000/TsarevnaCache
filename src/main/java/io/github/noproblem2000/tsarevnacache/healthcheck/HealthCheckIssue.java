package io.github.noproblem2000.tsarevnacache.healthcheck;

import java.time.Instant;
import java.util.Map;

/**
 * One finding of the analyzer.
 *
 * @param issueId            deterministic key, e.g. {@code MAP_UNBOUNDED_NO_TTL:user_sessions}; used by the ignore list
 * @param checkType          rule that found the issue
 * @param severity           {@code CRITICAL}, {@code WARNING} or {@code INFO}
 * @param targetType         kind of the affected object
 * @param targetName         map / queue name, {@code Cluster} or the DTO class
 * @param currentValue       measured values in technical form, e.g. {@code entries=12000, memory=450 MB, ttl=infinite}
 * @param recommendationCode translation key of the advice ({@code healthcheck.recommendations.<code>})
 * @param detectedAt         first run in which the issue was found (kept while it persists)
 * @param details            raw values for the UI (numbers, factory / class ids, affected maps)
 */
public record HealthCheckIssue(
        String issueId,
        HealthCheckType checkType,
        HealthSeverity severity,
        HealthTargetType targetType,
        String targetName,
        String currentValue,
        String recommendationCode,
        Instant detectedAt,
        Map<String, Object> details) {

    public HealthCheckIssue {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    /** The same issue with another detection time. */
    public HealthCheckIssue detectedAt(Instant time) {
        return new HealthCheckIssue(issueId, checkType, severity, targetType, targetName, currentValue,
                recommendationCode, time, details);
    }
}
