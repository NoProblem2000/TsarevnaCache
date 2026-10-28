package io.github.noproblem2000.tsarevnacache.api;

import io.github.noproblem2000.tsarevnacache.api.dto.ClusterOverviewDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.StructuresDTO;
import io.github.noproblem2000.tsarevnacache.cluster.ClusterService;
import io.github.noproblem2000.tsarevnacache.structures.StructureRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cluster overview and the registry of distributed objects.
 */
@RestController
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterService clusterService;
    private final StructureRegistryService registry;

    /** Connection state, members and aggregated map memory statistics. */
    @GetMapping("/api/cluster/overview")
    public ClusterOverviewDTO overview() {
        return clusterService.overview();
    }

    /** Distributed objects grouped by type with sizes and memory figures. */
    @GetMapping("/api/structures")
    public StructuresDTO structures(@RequestParam(defaultValue = "false") boolean includeInternal) {
        return registry.list(includeInternal);
    }
}
