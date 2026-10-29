package io.github.noproblem2000.tsarevnacache.healthcheck;

/** Overall result of a health check run. */
public enum HealthStatus {
    /** No active critical or warning issue. */
    HEALTHY,
    /** At least one active warning, no critical issue. */
    DEGRADED,
    /** At least one active critical issue, or the cluster could not be checked. */
    CRITICAL
}
