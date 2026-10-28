package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A distributed object.
 *
 * @param name                  object name
 * @param type                  structure type
 * @param serviceName           Hazelcast service name
 * @param internal              whether the object is a Hazelcast internal one (name starts with {@code __})
 * @param size                  number of entries/items when known
 * @param ownedEntryMemoryCost  owned memory in bytes (maps, from metrics)
 * @param backupEntryMemoryCost backup memory in bytes (maps, from metrics)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StructureDTO(
        String name,
        StructureType type,
        String serviceName,
        boolean internal,
        Long size,
        Long ownedEntryMemoryCost,
        Long backupEntryMemoryCost) {
}
