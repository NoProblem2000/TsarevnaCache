package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body of {@code POST /api/maps/{mapName}/entry} (create or update).
 *
 * <p>The key is given either as {@code key}+{@code keyType} or as {@code keyRef} (the opaque
 * serialized key returned by list/get endpoints, which also works for keys whose class is
 * unknown to the admin service). {@code keyRef} wins when both are present.
 *
 * @param key            key input
 * @param keyType        key type
 * @param keyClassName   key class for {@link DataType#CLASS}
 * @param keySchema      key schema for {@link DataType#COMPACT}
 * @param keyRef         serialized key reference
 * @param value          value input
 * @param valueType      value type
 * @param valueClassName value class for {@link DataType#CLASS}
 * @param valueSchema    value schema for {@link DataType#COMPACT}
 * @param ttlSeconds     optional time-to-live; {@code 0} means "never expire", {@code null} uses the map default
 * @param keyFactoryId   factory id of an {@link DataType#IDENTIFIED_DTO} key (project factory when omitted)
 * @param keyClassId     class id of an {@link DataType#IDENTIFIED_DTO} key (or use {@code keyClassName})
 * @param valueFactoryId factory id of an {@link DataType#IDENTIFIED_DTO} value (project factory when omitted)
 * @param valueClassId   class id of an {@link DataType#IDENTIFIED_DTO} value (or use {@code valueClassName})
 */
public record PutEntryRequest(
        JsonNode key,
        DataType keyType,
        String keyClassName,
        @Valid RecordSchemaDTO keySchema,
        String keyRef,
        @NotNull JsonNode value,
        @NotNull DataType valueType,
        String valueClassName,
        @Valid RecordSchemaDTO valueSchema,
        @PositiveOrZero Long ttlSeconds,
        Integer keyFactoryId,
        Integer keyClassId,
        Integer valueFactoryId,
        Integer valueClassId) {

    public TypedValueInput keyInput() {
        return new TypedValueInput(key, keyType, keyClassName, keySchema, keyFactoryId, keyClassId);
    }

    public TypedValueInput valueInput() {
        return new TypedValueInput(value, valueType, valueClassName, valueSchema, valueFactoryId, valueClassId);
    }
}
