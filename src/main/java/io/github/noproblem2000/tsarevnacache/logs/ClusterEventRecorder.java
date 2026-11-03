package io.github.noproblem2000.tsarevnacache.logs;

import com.hazelcast.cluster.InitialMembershipEvent;
import com.hazelcast.cluster.InitialMembershipListener;
import com.hazelcast.cluster.Member;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.core.DistributedObjectEvent;
import com.hazelcast.core.DistributedObjectListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleEvent;
import com.hazelcast.core.LifecycleListener;
import com.hazelcast.partition.MigrationListener;
import com.hazelcast.partition.MigrationState;
import com.hazelcast.partition.PartitionLostEvent;
import com.hazelcast.partition.PartitionLostListener;
import com.hazelcast.partition.ReplicaMigrationEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Records cluster events in the live stream: members joining and leaving
 * ({@link MembershipListener}), partition migrations and lost partitions ({@link MigrationListener},
 * {@link PartitionLostListener}) and structures being created or destroyed
 * ({@link DistributedObjectListener}). Client state changes come from {@link LifecycleRecorder},
 * which is registered in the client configuration so the very first states are seen as well.
 */
@Slf4j
@Component
public class ClusterEventRecorder {

    private final HazelcastInstance hazelcast;
    private final LiveEventBuffer buffer;
    private final List<Runnable> deregistrations = new ArrayList<>();

    public ClusterEventRecorder(HazelcastInstance hazelcastClient, LiveEventBuffer buffer) {
        this.hazelcast = hazelcastClient;
        this.buffer = buffer;
    }

    @PostConstruct
    void register() {
        listen("MembershipListener", () -> hazelcast.getCluster().addMembershipListener(new Membership()),
                id -> hazelcast.getCluster().removeMembershipListener(id));
        listen("MigrationListener", () -> hazelcast.getPartitionService().addMigrationListener(new Migrations()),
                id -> hazelcast.getPartitionService().removeMigrationListener(id));
        listen("PartitionLostListener",
                () -> hazelcast.getPartitionService().addPartitionLostListener(new LostPartitions()),
                id -> hazelcast.getPartitionService().removePartitionLostListener(id));
        listen("DistributedObjectListener", () -> hazelcast.addDistributedObjectListener(new Structures()),
                hazelcast::removeDistributedObjectListener);
    }

    @PreDestroy
    void deregister() {
        if (hazelcast.getLifecycleService().isRunning()) {
            deregistrations.forEach(Runnable::run);
        }
        deregistrations.clear();
    }

    private void listen(String name, Supplier<UUID> add, Consumer<UUID> remove) {
        try {
            UUID id = add.get();
            deregistrations.add(() -> {
                try {
                    remove.accept(id);
                } catch (RuntimeException e) {
                    log.debug("Cannot remove the {}: {}", name, e.getMessage());
                }
            });
        } catch (RuntimeException e) {
            log.warn("Cluster events of the {} are not recorded: {}", name, e.getMessage());
            buffer.add(LogLevel.WARN, LiveEventSource.LOG, ClusterEventRecorder.class.getName(),
                    name + " could not be registered: " + e.getMessage());
        }
    }

    private static String describe(Member member) {
        return member.getAddress() + " (" + member.getUuid() + (member.isLiteMember() ? ", lite member" : "") + ")";
    }

    private final class Membership implements InitialMembershipListener {

        @Override
        public void init(InitialMembershipEvent event) {
            buffer.add(LogLevel.INFO, LiveEventSource.MEMBERSHIP, MembershipListener.class.getName(),
                    "Cluster members (" + event.getMembers().size() + "): "
                            + String.join(", ", event.getMembers().stream().map(ClusterEventRecorder::describe).toList()));
        }

        @Override
        public void memberAdded(MembershipEvent event) {
            buffer.add(LogLevel.INFO, LiveEventSource.MEMBERSHIP, MembershipListener.class.getName(),
                    "Member joined: " + describe(event.getMember()) + "; cluster size " + event.getMembers().size());
        }

        @Override
        public void memberRemoved(MembershipEvent event) {
            buffer.add(LogLevel.WARN, LiveEventSource.MEMBERSHIP, MembershipListener.class.getName(),
                    "Member left: " + describe(event.getMember()) + "; cluster size " + event.getMembers().size());
        }
    }

    private final class Migrations implements MigrationListener {

        @Override
        public void migrationStarted(MigrationState state) {
            buffer.add(LogLevel.INFO, LiveEventSource.MIGRATION, MigrationListener.class.getName(),
                    "Partition migration started: " + state.getPlannedMigrations() + " planned migrations");
        }

        @Override
        public void migrationFinished(MigrationState state) {
            boolean failed = state.getRemainingMigrations() > 0;
            buffer.add(failed ? LogLevel.WARN : LogLevel.INFO, LiveEventSource.MIGRATION,
                    MigrationListener.class.getName(),
                    "Partition migration finished: " + state.getCompletedMigrations() + " of "
                            + state.getPlannedMigrations() + " completed, " + state.getRemainingMigrations()
                            + " remaining, " + state.getTotalElapsedTime() + " ms");
        }

        @Override
        public void replicaMigrationCompleted(ReplicaMigrationEvent event) {
            // one event per partition replica: too many for the stream, the summary above is enough
        }

        @Override
        public void replicaMigrationFailed(ReplicaMigrationEvent event) {
            buffer.add(LogLevel.WARN, LiveEventSource.MIGRATION, MigrationListener.class.getName(),
                    "Migration of partition " + event.getPartitionId() + " (replica " + event.getReplicaIndex()
                            + ") failed: " + event.getSource() + " -> " + event.getDestination());
        }
    }

    private final class LostPartitions implements PartitionLostListener {

        @Override
        public void partitionLost(PartitionLostEvent event) {
            buffer.add(LogLevel.ERROR, LiveEventSource.MIGRATION, PartitionLostListener.class.getName(),
                    "Partition " + event.getPartitionId() + " lost (" + event.getLostBackupCount()
                            + " backups lost, detected by " + event.getEventSource() + ")");
        }
    }

    private final class Structures implements DistributedObjectListener {

        @Override
        public void distributedObjectCreated(DistributedObjectEvent event) {
            buffer.add(LogLevel.INFO, LiveEventSource.DISTRIBUTED_OBJECT, DistributedObjectListener.class.getName(),
                    "Created " + event.getServiceName() + " '" + event.getObjectName() + "'");
        }

        @Override
        public void distributedObjectDestroyed(DistributedObjectEvent event) {
            buffer.add(LogLevel.WARN, LiveEventSource.DISTRIBUTED_OBJECT, DistributedObjectListener.class.getName(),
                    "Destroyed " + event.getServiceName() + " '" + event.getObjectName() + "'");
        }
    }

    /** Client state changes ({@code STARTING}, {@code CLIENT_CONNECTED}, {@code CLIENT_DISCONNECTED}, ...). */
    public static final class LifecycleRecorder implements LifecycleListener {

        private final LiveEventBuffer buffer;
        private final String clusterName;

        public LifecycleRecorder(LiveEventBuffer buffer, String clusterName) {
            this.buffer = buffer;
            this.clusterName = clusterName;
        }

        @Override
        public void stateChanged(LifecycleEvent event) {
            LogLevel level = switch (event.getState()) {
                case CLIENT_DISCONNECTED, SHUTTING_DOWN, SHUTDOWN -> LogLevel.WARN;
                default -> LogLevel.INFO;
            };
            buffer.add(level, LiveEventSource.LIFECYCLE, LifecycleListener.class.getName(),
                    "Client state " + event.getState() + " (cluster '" + clusterName + "')");
        }
    }
}
