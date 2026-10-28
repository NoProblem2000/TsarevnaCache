package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A map entry as returned by the IMap inspector.
 *
 * @param keyRef   opaque serialized key; send it back to get/update/delete this exact entry
 * @param key      inspected key
 * @param value    inspected value
 * @param metadata entry metadata, {@code null} when not requested
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EntryDTO(
        String keyRef,
        InspectedValue key,
        InspectedValue value,
        EntryMetadataDTO metadata) {
}
