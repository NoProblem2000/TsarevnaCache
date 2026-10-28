package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.Builder;

/**
 * Result of inspecting a key or value read from the cluster.
 *
 * @param deserialized   {@code false} when the binary could not be decoded (see {@code unresolved})
 * @param kind           display category
 * @param javaType       Java class name, or the Compact type name for Compact records
 * @param dataType       type to pre-select when editing; {@code null} if the value is read-only
 * @param value          full JSON representation; omitted in list views and for unresolved objects
 * @param preview        compact single-line text, truncated to the configured preview length
 * @param truncated      whether {@code preview} was truncated
 * @param schema         structure of Compact/Portable records
 * @param serializedSize size of the serialized binary in bytes (when read from binary)
 * @param unresolved     failure details when {@code deserialized} is {@code false}
 * @param warning        non fatal problem, e.g. {@link InspectionWarning#JSON_CONVERSION_FAILED}
 * @param metadata       additional type specific details
 */
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InspectedValue(
        boolean deserialized,
        ValueKind kind,
        String javaType,
        DataType dataType,
        JsonNode value,
        String preview,
        boolean truncated,
        RecordSchemaDTO schema,
        Integer serializedSize,
        UnresolvedObjectDTO unresolved,
        InspectionWarning warning,
        Map<String, Object> metadata) {

    /** Returns a copy without the full JSON value (used by paged list views). */
    public InspectedValue withoutValue() {
        return toBuilder().value(null).build();
    }

    /** Returns a copy whose unresolved details carry the memory cost of the map entry. */
    public InspectedValue withEntryCost(Long entryCost) {
        return unresolved == null ? this : toBuilder().unresolved(unresolved.withEntryCost(entryCost)).build();
    }

    /** Non fatal inspection problems. */
    public enum InspectionWarning {
        /** The object was deserialized but could not be converted to JSON; {@code value} holds its {@code toString()}. */
        JSON_CONVERSION_FAILED
    }
}
