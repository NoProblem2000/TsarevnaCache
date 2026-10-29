package io.github.noproblem2000.tsarevnacache.healthcheck;

/** Severity of a health check issue; {@code INFO} does not lower the health score. */
public enum HealthSeverity {
    CRITICAL,
    WARNING,
    INFO
}
