package io.github.noproblem2000.tsarevnacache.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Response of {@code GET /api/structures}: distributed objects grouped by type.
 *
 * @param total  number of objects listed
 * @param groups objects per type, sorted by name; every type is present (possibly empty)
 * @param stats  availability of the metrics behind sizes and memory figures
 */
public record StructuresDTO(int total, Map<StructureType, List<StructureDTO>> groups, StatsStatusDTO stats) {
}
