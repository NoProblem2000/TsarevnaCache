package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * A raw key or value supplied by the user together with the type it must be converted to.
 *
 * @param raw       the input; scalars may be given as JSON scalars or as text, JSON/CLASS/COMPACT/
 *                  IDENTIFIED_DTO values as JSON or as text containing JSON
 * @param type      target type
 * @param className fully qualified class name for {@link DataType#CLASS}; DTO class name for
 *                  {@link DataType#IDENTIFIED_DTO} (alternative to {@code classId})
 * @param schema    record schema, required for {@link DataType#COMPACT}
 * @param factoryId factory id for {@link DataType#IDENTIFIED_DTO}, the project's factory when omitted
 * @param classId   class id for {@link DataType#IDENTIFIED_DTO}
 */
public record TypedValueInput(JsonNode raw, DataType type, String className, RecordSchemaDTO schema,
                              Integer factoryId, Integer classId) {

    public TypedValueInput(JsonNode raw, DataType type, String className, RecordSchemaDTO schema) {
        this(raw, type, className, schema, null, null);
    }

    /** Input coming from a query parameter. */
    public static TypedValueInput ofText(String text, DataType type, String className) {
        return new TypedValueInput(text == null ? null : TextNode.valueOf(text), type, className, null);
    }
}
