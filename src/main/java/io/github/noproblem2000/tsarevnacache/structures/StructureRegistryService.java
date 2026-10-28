package io.github.noproblem2000.tsarevnacache.structures;

import com.hazelcast.collection.IList;
import com.hazelcast.collection.ISet;
import com.hazelcast.core.DistributedObject;
import io.github.noproblem2000.tsarevnacache.api.dto.StructureDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.StructureType;
import io.github.noproblem2000.tsarevnacache.api.dto.StructuresDTO;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.hazelcast.ClientAccess;
import io.github.noproblem2000.tsarevnacache.metrics.ClusterMetrics;
import io.github.noproblem2000.tsarevnacache.metrics.ClusterMetricsService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Lists distributed objects and checks their existence (without creating them: calling
 * {@code getMap(name)} for an unknown name would create a new map in the cluster).
 */
@Slf4j
@Service
public class StructureRegistryService {

    private final ClientAccess clientAccess;
    private final ClusterMetricsService metricsService;

    public StructureRegistryService(ClientAccess clientAccess, ClusterMetricsService metricsService) {
        this.clientAccess = clientAccess;
        this.metricsService = metricsService;
    }

    /** Distributed objects grouped by type, with sizes and memory figures. */
    public StructuresDTO list(boolean includeInternal) {
        clientAccess.ensureConnected();
        ClusterMetrics metrics = metricsService.current();
        Map<StructureType, List<StructureDTO>> groups = new EnumMap<>(StructureType.class);
        for (StructureType type : StructureType.values()) {
            groups.put(type, new ArrayList<>());
        }
        int total = 0;
        for (DistributedObject object : clientAccess.hazelcast().getDistributedObjects()) {
            boolean internal = isInternal(object.getName());
            if (internal && !includeInternal) {
                continue;
            }
            StructureType type = StructureType.fromServiceName(object.getServiceName());
            groups.get(type).add(describe(object, type, internal, metrics));
            total++;
        }
        groups.values().forEach(list -> list.sort(Comparator.comparing(StructureDTO::name)));
        return new StructuresDTO(total, groups, metrics.status());
    }

    /** Whether an object of the given type and name exists in the cluster. */
    public boolean exists(StructureType type, String name) {
        return clientAccess.hazelcast().getDistributedObjects().stream()
                .anyMatch(object -> object.getName().equals(name) && type.serviceName() != null
                        && type.serviceName().equals(object.getServiceName()));
    }

    /**
     * @throws ApiException with {@link ErrorCode#MAP_NOT_FOUND} / {@link ErrorCode#STRUCTURE_NOT_FOUND}
     */
    public void requireExists(StructureType type, String name) {
        clientAccess.ensureConnected();
        if (!exists(type, name)) {
            ErrorCode code = type == StructureType.MAP ? ErrorCode.MAP_NOT_FOUND : ErrorCode.STRUCTURE_NOT_FOUND;
            throw new ApiException(code, type + " '" + name + "' does not exist",
                    Map.of("type", type.name(), "name", name));
        }
    }

    /** Hazelcast internal objects (SQL catalog, Jet, MC, ...) start with two underscores. */
    public static boolean isInternal(String name) {
        return name.startsWith("__");
    }

    private StructureDTO describe(DistributedObject object, StructureType type, boolean internal,
                                  ClusterMetrics metrics) {
        String name = object.getName();
        Long size = null;
        Long owned = null;
        Long backup = null;
        try {
            switch (type) {
                case MAP -> {
                    if (metrics.has(ClusterMetrics.MAP, name)) {
                        size = metrics.sum(ClusterMetrics.MAP, name, "ownedEntryCount");
                        owned = metrics.sum(ClusterMetrics.MAP, name, "ownedEntryMemoryCost");
                        backup = metrics.sum(ClusterMetrics.MAP, name, "backupEntryMemoryCost");
                    } else {
                        size = (long) clientAccess.hazelcast().getMap(name).size();
                    }
                }
                case MULTI_MAP -> size = metrics.has(ClusterMetrics.MULTI_MAP, name)
                        ? metrics.sum(ClusterMetrics.MULTI_MAP, name, "ownedEntryCount")
                        : (long) clientAccess.hazelcast().getMultiMap(name).size();
                // every member holds a full copy of a replicated map
                case REPLICATED_MAP -> size = (long) clientAccess.hazelcast().getReplicatedMap(name).size();
                case QUEUE -> size = metrics.has(ClusterMetrics.QUEUE, name)
                        ? metrics.sum(ClusterMetrics.QUEUE, name, "ownedItemCount")
                        : (long) clientAccess.hazelcast().getQueue(name).size();
                case SET -> size = (long) ((ISet<?>) object).size();
                case LIST -> size = (long) ((IList<?>) object).size();
                default -> {
                    // topics, flake id generators and unknown services have no size
                }
            }
        } catch (RuntimeException e) {
            log.debug("Cannot determine size of {} {}", type, name, e);
        }
        return new StructureDTO(name, type, object.getServiceName(), internal, size, owned, backup);
    }
}
