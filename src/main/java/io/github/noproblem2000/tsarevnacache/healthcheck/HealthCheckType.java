package io.github.noproblem2000.tsarevnacache.healthcheck;

/**
 * Rules of the best practices analyzer. The name is the {@code checkType} of an issue and the
 * prefix of its {@code issueId} ({@code MAP_UNBOUNDED_NO_TTL:user_sessions}); the UI translates it
 * as {@code healthcheck.checks.<name>}.
 */
public enum HealthCheckType {

    /** The cluster has a single data member: no fault tolerance, backups have nowhere to go. */
    CLUSTER_SINGLE_MEMBER(HealthTargetType.CLUSTER),

    /** A large map whose sampled entries never expire and are never evicted by max idle. */
    MAP_UNBOUNDED_NO_TTL(HealthTargetType.IMAP),

    /** A map that is written but never read ("dead cache"). */
    MAP_ZOMBIE_WRITE_ONLY(HealthTargetType.IMAP),

    /** A map without backup entries although the cluster has several data members. */
    MAP_BACKUP_DISABLED_OR_BROKEN(HealthTargetType.IMAP),

    /** Average get or put latency of a map above the threshold. */
    MAP_HIGH_LATENCY(HealthTargetType.IMAP),

    /** A queue that grows beyond the backlog threshold. */
    QUEUE_BACKLOG_GROWTH(HealthTargetType.IQUEUE),

    /** Sampled IdentifiedDataSerializable values whose factory id / class id has no DTO schema. */
    DTO_UNREGISTERED_SCHEMA(HealthTargetType.DTO_SCHEMA);

    private final HealthTargetType targetType;

    HealthCheckType(HealthTargetType targetType) {
        this.targetType = targetType;
    }

    public HealthTargetType targetType() {
        return targetType;
    }

    /** Deterministic issue id: {@code <checkType>:<target>}. */
    public String issueId(String target) {
        return name() + ":" + target;
    }
}
