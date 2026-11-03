package io.github.noproblem2000.tsarevnacache.logs;

/** Origin of a live stream event. */
public enum LiveEventSource {
    /** A {@code com.hazelcast} or admin service log event (Logback appender). */
    LOG,
    /** {@code MembershipListener}: a member joined or left the cluster. */
    MEMBERSHIP,
    /** {@code LifecycleListener}: the admin client changed its state (connected, disconnected, ...). */
    LIFECYCLE,
    /** {@code MigrationListener} / {@code PartitionLostListener}: partition migrations and lost partitions. */
    MIGRATION,
    /** {@code DistributedObjectListener}: a map or another structure was created or destroyed. */
    DISTRIBUTED_OBJECT
}
