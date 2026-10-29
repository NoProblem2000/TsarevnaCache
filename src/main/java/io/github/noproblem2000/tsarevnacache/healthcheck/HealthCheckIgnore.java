package io.github.noproblem2000.tsarevnacache.healthcheck;

import java.time.Instant;

/**
 * Entry of {@code config/healthcheck-ignores.json}.
 *
 * @param issueId   an exact issue id ({@code MAP_UNBOUNDED_NO_TTL:countries}) or a whole rule
 *                  ({@code MAP_UNBOUNDED_NO_TTL:*} or just {@code MAP_UNBOUNDED_NO_TTL})
 * @param reason    why the finding does not apply, e.g. "static reference data"
 * @param createdAt when the entry was added
 * @param createdBy administrator who added it
 */
public record HealthCheckIgnore(String issueId, String reason, Instant createdAt, String createdBy) {

    /** Whether this entry hides the issue. */
    public boolean matches(HealthCheckIssue issue) {
        String rule = issue.checkType().name();
        return issueId.equals(issue.issueId()) || issueId.equals(rule) || issueId.equals(rule + ":*");
    }

    /** {@code true} for an entry that hides every issue of a rule. */
    public boolean wholeRule() {
        return !issueId.contains(":") || issueId.endsWith(":*");
    }
}
