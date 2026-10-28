package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.Builder;

/**
 * Describes a stored object the admin client could not deserialize. Returned instead of failing
 * the request, so a single unreadable entry never breaks a page of results.
 *
 * @param deserialized   always {@code false}
 * @param errorCode      reason category, used for localization
 * @param reason         technical message of the underlying failure
 * @param missingClass   class (or factory description) that is missing, when known
 * @param typeId         Hazelcast serializer type id of the binary
 * @param serializer     human readable serializer family, e.g. {@code Java Serializable}
 * @param serializedSize size of the serialized binary in bytes
 * @param entryCost      memory cost of the whole map entry ({@code EntryView.getCost()}), when known
 * @param metadata       details read from the binary header (factory/class ids, schema id, ...)
 */
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnresolvedObjectDTO(
        boolean deserialized,
        DeserializationErrorCode errorCode,
        String reason,
        String missingClass,
        int typeId,
        String serializer,
        Integer serializedSize,
        Long entryCost,
        Map<String, Object> metadata) {

    public UnresolvedObjectDTO {
        deserialized = false;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Returns a copy carrying the memory cost of the map entry. */
    public UnresolvedObjectDTO withEntryCost(Long cost) {
        return toBuilder().entryCost(cost).build();
    }
}
