package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Response of {@code GET /api/other/{type}/{name}}.
 *
 * @param type           structure type
 * @param name           structure name
 * @param size           number of items (values for multimaps)
 * @param limit          maximum number of items returned
 * @param itemsAvailable {@code false} when the structure is too large to be fetched by a client
 * @param items          the first items
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtherStructureDTO(
        StructureType type,
        String name,
        long size,
        int limit,
        boolean itemsAvailable,
        List<Item> items) {

    /**
     * An item. Queues, sets and lists fill {@code ref}/{@code value}; multimaps additionally fill
     * {@code keyRef}/{@code key}; replicated maps fill {@code keyRef}/{@code key}/{@code value}.
     *
     * @param ref    serialized value reference (used to remove this value)
     * @param value  inspected value
     * @param keyRef serialized key reference
     * @param key    inspected key
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String ref, InspectedValue value, String keyRef, InspectedValue key) {
    }
}
