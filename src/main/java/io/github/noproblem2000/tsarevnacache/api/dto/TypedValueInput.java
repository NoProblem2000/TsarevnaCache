package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * A raw key or value supplied by the user together with the type it must be converted to.
 *
 * @param raw       the input; scalars may be given as JSON scalars or as text, JSON/CLASS/COMPACT
 *                  values as JSON or as text containing JSON
 * @param type      target type
 * @param className fully qualified class name, required for {@link DataType#CLASS}
 * @param schema    record schema, required for {@link DataType#COMPACT}
 */
public record TypedValueInput(JsonNode raw, DataType type, String className, RecordSchemaDTO schema) {

    /** Input coming from a query parameter. */
    public static TypedValueInput ofText(String text, DataType type, String className) {
        return new TypedValueInput(text == null ? null : TextNode.valueOf(text), type, className, null);
    }
}
