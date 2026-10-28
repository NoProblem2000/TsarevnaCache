package io.github.noproblem2000.tsarevnacache.cluster;

import com.hazelcast.cluster.Address;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.DistributedObject;
import com.hazelcast.instance.BuildInfoProvider;
import io.github.noproblem2000.tsarevnacache.api.dto.ByteSize;
import io.github.noproblem2000.tsarevnacache.api.dto.ClusterOverviewDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.ClusterOverviewDTO.MapMemory;
import io.github.noproblem2000.tsarevnacache.api.dto.ClusterOverviewDTO.Totals;
import io.github.noproblem2000.tsarevnacache.api.dto.MemberDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.StructureType;
import io.github.noproblem2000.tsarevnacache.config.properties.HazelcastClientProperties;
import io.github.noproblem2000.tsarevnacache.hazelcast.ClientAccess;
import io.github.noproblem2000.tsarevnacache.metrics.ClusterMetrics;
import io.github.noproblem2000.tsarevnacache.metrics.ClusterMetricsService;
import io.github.noproblem2000.tsarevnacache.metrics.MemberMetrics;
import io.github.noproblem2000.tsarevnacache.structures.StructureRegistryService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Builds the cluster overview: connection state, members and aggregated map statistics.
 */
@Slf4j
@Service
public class ClusterService {

    private static final int TOP_MAPS = 5;

    private final ClientAccess clientAccess;
    private final ClusterMetricsService metricsService;
    private final HazelcastClientProperties clientProperties;

    public ClusterService(ClientAccess clientAccess, ClusterMetricsService metricsService,
                          HazelcastClientProperties clientProperties) {
        this.clientAccess = clientAccess;
        this.metricsService = metricsService;
        this.clientProperties = clientProperties;
    }

    /**
     * Never fails because the cluster is down: it then reports {@code connected=false} and no data.
     */
    public ClusterOverviewDTO overview() {
        String clientVersion = BuildInfoProvider.getBuildInfo().getVersion();
        if (!clientAccess.isConnected()) {
            return new ClusterOverviewDTO(false, clientProperties.getClusterName(), clientVersion, null,
                    List.of(), new Totals(0, 0, 0, 0, ByteSize.format(0), 0, ByteSize.format(0)),
                    emptyCounts(), List.of(), metricsService.current().status());
        }

        ClusterMetrics metrics = metricsService.current();
        List<MemberDTO> members = members(metrics);
        String clusterVersion = members.isEmpty() ? null : members.get(0).version();

        Map<StructureType, Integer> counts = emptyCounts();
        List<String> mapNames = new ArrayList<>();
        int objects = 0;
        for (DistributedObject object : clientAccess.hazelcast().getDistributedObjects()) {
            if (StructureRegistryService.isInternal(object.getName())) {
                continue;
            }
            StructureType type = StructureType.fromServiceName(object.getServiceName());
            counts.merge(type, 1, Integer::sum);
            objects++;
            if (type == StructureType.MAP) {
                mapNames.add(object.getName());
            }
        }

        List<MapMemory> maps = mapNames.stream()
                .map(name -> new MapMemory(name,
                        metrics.sum(ClusterMetrics.MAP, name, "ownedEntryCount"),
                        metrics.sum(ClusterMetrics.MAP, name, "ownedEntryMemoryCost"),
                        metrics.sum(ClusterMetrics.MAP, name, "backupEntryMemoryCost")))
                .toList();
        long entries = maps.stream().mapToLong(MapMemory::ownedEntryCount).sum();
        long owned = maps.stream().mapToLong(MapMemory::ownedEntryMemoryCost).sum();
        long backup = maps.stream().mapToLong(MapMemory::backupEntryMemoryCost).sum();
        List<MapMemory> top = maps.stream()
                .sorted(Comparator.comparingLong(MapMemory::ownedEntryMemoryCost).reversed()
                        .thenComparing(MapMemory::name))
                .limit(TOP_MAPS)
                .toList();

        Totals totals = new Totals(objects, mapNames.size(), entries, owned, ByteSize.format(owned),
                backup, ByteSize.format(backup));
        return new ClusterOverviewDTO(true, clientProperties.getClusterName(), clientVersion, clusterVersion,
                members, totals, counts, top, metrics.status());
    }

    private List<MemberDTO> members(ClusterMetrics metrics) {
        List<MemberDTO> result = new ArrayList<>();
        boolean first = true;
        for (Member member : clientAccess.hazelcast().getCluster().getMembers()) {
            Address address = member.getAddress();
            MemberMetrics memberMetrics = metrics.member(member.getUuid()).orElse(null);
            result.add(new MemberDTO(
                    member.getUuid().toString(),
                    address.getHost() + ":" + address.getPort(),
                    address.getHost(),
                    address.getPort(),
                    member.isLiteMember(),
                    first,
                    member.getVersion() == null ? null : member.getVersion().toString(),
                    memberMetrics == null ? null : memberMetrics.global("runtime.usedMemory"),
                    memberMetrics == null ? null : memberMetrics.global("runtime.maxMemory"),
                    memberMetrics == null ? null : memberMetrics.global("runtime.uptime")));
            first = false;
        }
        return result;
    }

    private static Map<StructureType, Integer> emptyCounts() {
        Map<StructureType, Integer> counts = new EnumMap<>(StructureType.class);
        for (StructureType type : StructureType.values()) {
            counts.put(type, 0);
        }
        return counts;
    }
}
