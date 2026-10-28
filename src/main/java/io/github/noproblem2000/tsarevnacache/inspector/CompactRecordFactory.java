package io.github.noproblem2000.tsarevnacache.inspector;

import com.fasterxml.jackson.databind.JsonNode;
import com.hazelcast.nio.serialization.FieldKind;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;
import io.github.noproblem2000.tsarevnacache.api.dto.RecordFieldDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.RecordSchemaDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.RecordSchemaDTO.RecordFormat;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Builds Compact {@link GenericRecord}s from JSON and a {@link RecordSchemaDTO}, so Compact values
 * can be created or edited without their Java classes. The resulting binary is identical to what
 * the owning application writes as long as the type name, field names and kinds match.
 */
final class CompactRecordFactory {

    private CompactRecordFactory() {
    }

    static GenericRecord build(RecordSchemaDTO schema, JsonNode json) {
        return build(schema, json, "$");
    }

    private static GenericRecord build(RecordSchemaDTO schema, JsonNode json, String path) {
        if (schema == null) {
            throw schemaError(path, "Schema of a nested Compact record is unknown", Map.of());
        }
        if (schema.format() != RecordFormat.COMPACT) {
            throw schemaError(path, "Only Compact records can be written", Map.of("format", String.valueOf(schema.format())));
        }
        if (json == null || !json.isObject()) {
            throw schemaError(path, "A JSON object is expected for a Compact record", Map.of());
        }
        Set<String> known = new HashSet<>();
        GenericRecordBuilder builder = GenericRecordBuilder.compact(schema.typeName());
        for (RecordFieldDTO field : schema.fields()) {
            known.add(field.name());
            String fieldPath = path + "." + field.name();
            try {
                setField(builder, field, json.get(field.name()), fieldPath);
            } catch (ArithmeticException e) {
                throw new ApiException(ErrorCode.VALUE_CONVERSION_FAILED,
                        "Number is out of range for " + field.kind() + " (" + fieldPath + ")",
                        Map.of("path", fieldPath, "expected", field.kind().name()));
            }
        }
        for (Iterator<String> names = json.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!known.contains(name)) {
                throw schemaError(path + "." + name, "Field is not part of the schema", Map.of());
            }
        }
        return builder.build();
    }

    private static void setField(GenericRecordBuilder b, RecordFieldDTO field, JsonNode node, String path) {
        String n = field.name();
        FieldKind kind = field.kind();
        boolean missing = node == null || node.isNull() || node.isMissingNode();
        switch (kind) {
            case BOOLEAN -> b.setBoolean(n, requireBoolean(node, path));
            case INT8 -> b.setInt8(n, requireDecimal(node, path).byteValueExact());
            case INT16 -> b.setInt16(n, requireDecimal(node, path).shortValueExact());
            case INT32 -> b.setInt32(n, requireDecimal(node, path).intValueExact());
            case INT64 -> b.setInt64(n, requireDecimal(node, path).longValueExact());
            case FLOAT32 -> b.setFloat32(n, requireDecimal(node, path).floatValue());
            case FLOAT64 -> b.setFloat64(n, requireDecimal(node, path).doubleValue());
            case ARRAY_OF_BOOLEAN -> b.setArrayOfBoolean(n, missing ? null : toBooleanArray(node, path));
            case ARRAY_OF_INT8 -> b.setArrayOfInt8(n, missing ? null : toByteArray(node, path));
            case ARRAY_OF_INT16 -> b.setArrayOfInt16(n, missing ? null : toShortArray(node, path));
            case ARRAY_OF_INT32 -> b.setArrayOfInt32(n, missing ? null : toIntArray(node, path));
            case ARRAY_OF_INT64 -> b.setArrayOfInt64(n, missing ? null : toLongArray(node, path));
            case ARRAY_OF_FLOAT32 -> b.setArrayOfFloat32(n, missing ? null : toFloatArray(node, path));
            case ARRAY_OF_FLOAT64 -> b.setArrayOfFloat64(n, missing ? null : toDoubleArray(node, path));
            case STRING -> b.setString(n, missing ? null : text(node));
            case ARRAY_OF_STRING -> b.setArrayOfString(n, array(node, path, String.class, CompactRecordFactory::text));
            case DECIMAL -> b.setDecimal(n, missing ? null : decimal(node, path));
            case ARRAY_OF_DECIMAL -> b.setArrayOfDecimal(n, array(node, path, BigDecimal.class, v -> decimal(v, path)));
            case TIME -> b.setTime(n, missing ? null : parse(node, path, LocalTime::parse));
            case ARRAY_OF_TIME -> b.setArrayOfTime(n, array(node, path, LocalTime.class, v -> parse(v, path, LocalTime::parse)));
            case DATE -> b.setDate(n, missing ? null : parse(node, path, LocalDate::parse));
            case ARRAY_OF_DATE -> b.setArrayOfDate(n, array(node, path, LocalDate.class, v -> parse(v, path, LocalDate::parse)));
            case TIMESTAMP -> b.setTimestamp(n, missing ? null : parse(node, path, LocalDateTime::parse));
            case ARRAY_OF_TIMESTAMP -> b.setArrayOfTimestamp(n,
                    array(node, path, LocalDateTime.class, v -> parse(v, path, LocalDateTime::parse)));
            case TIMESTAMP_WITH_TIMEZONE -> b.setTimestampWithTimezone(n,
                    missing ? null : parse(node, path, OffsetDateTime::parse));
            case ARRAY_OF_TIMESTAMP_WITH_TIMEZONE -> b.setArrayOfTimestampWithTimezone(n,
                    array(node, path, OffsetDateTime.class, v -> parse(v, path, OffsetDateTime::parse)));
            case COMPACT -> b.setGenericRecord(n, missing ? null : build(field.schema(), node, path));
            case ARRAY_OF_COMPACT -> b.setArrayOfGenericRecord(n,
                    array(node, path, GenericRecord.class, v -> build(field.schema(), v, path + "[]")));
            case NULLABLE_BOOLEAN -> b.setNullableBoolean(n, missing ? null : requireBoolean(node, path));
            case NULLABLE_INT8 -> b.setNullableInt8(n, missing ? null : requireDecimal(node, path).byteValueExact());
            case NULLABLE_INT16 -> b.setNullableInt16(n, missing ? null : requireDecimal(node, path).shortValueExact());
            case NULLABLE_INT32 -> b.setNullableInt32(n, missing ? null : requireDecimal(node, path).intValueExact());
            case NULLABLE_INT64 -> b.setNullableInt64(n, missing ? null : requireDecimal(node, path).longValueExact());
            case NULLABLE_FLOAT32 -> b.setNullableFloat32(n, missing ? null : requireDecimal(node, path).floatValue());
            case NULLABLE_FLOAT64 -> b.setNullableFloat64(n, missing ? null : requireDecimal(node, path).doubleValue());
            case ARRAY_OF_NULLABLE_BOOLEAN -> b.setArrayOfNullableBoolean(n,
                    array(node, path, Boolean.class, v -> requireBoolean(v, path)));
            case ARRAY_OF_NULLABLE_INT8 -> b.setArrayOfNullableInt8(n,
                    array(node, path, Byte.class, v -> requireDecimal(v, path).byteValueExact()));
            case ARRAY_OF_NULLABLE_INT16 -> b.setArrayOfNullableInt16(n,
                    array(node, path, Short.class, v -> requireDecimal(v, path).shortValueExact()));
            case ARRAY_OF_NULLABLE_INT32 -> b.setArrayOfNullableInt32(n,
                    array(node, path, Integer.class, v -> requireDecimal(v, path).intValueExact()));
            case ARRAY_OF_NULLABLE_INT64 -> b.setArrayOfNullableInt64(n,
                    array(node, path, Long.class, v -> requireDecimal(v, path).longValueExact()));
            case ARRAY_OF_NULLABLE_FLOAT32 -> b.setArrayOfNullableFloat32(n,
                    array(node, path, Float.class, v -> requireDecimal(v, path).floatValue()));
            case ARRAY_OF_NULLABLE_FLOAT64 -> b.setArrayOfNullableFloat64(n,
                    array(node, path, Double.class, v -> requireDecimal(v, path).doubleValue()));
            case CHAR, ARRAY_OF_CHAR, PORTABLE, ARRAY_OF_PORTABLE, NOT_AVAILABLE ->
                    throw schemaError(path, "Field kind is not supported by Compact serialization",
                            Map.of("kind", kind.name()));
        }
    }

    // ------------------------------------------------------------------ scalar conversions

    private static boolean requireBoolean(JsonNode node, String path) {
        requirePresent(node, path);
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        String text = node.asText().trim();
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.parseBoolean(text);
        }
        throw conversionError(path, "boolean", node);
    }

    private static BigDecimal requireDecimal(JsonNode node, String path) {
        requirePresent(node, path);
        return decimal(node, path);
    }

    private static BigDecimal decimal(JsonNode node, String path) {
        try {
            return node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText().trim());
        } catch (NumberFormatException e) {
            throw conversionError(path, "number", node);
        }
    }

    private static String text(JsonNode node) {
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private static <T> T parse(JsonNode node, String path, Function<String, T> parser) {
        try {
            return parser.apply(node.asText().trim());
        } catch (RuntimeException e) {
            throw conversionError(path, "ISO-8601 date/time", node);
        }
    }

    private static void requirePresent(JsonNode node, String path) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            throw schemaError(path, "A value is required for a non-nullable field", Map.of());
        }
    }

    // ------------------------------------------------------------------ arrays

    private static <T> T[] array(JsonNode node, String path, Class<T> type, Function<JsonNode, T> element) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (!node.isArray()) {
            throw conversionError(path, "array", node);
        }
        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(type, node.size());
        for (int i = 0; i < node.size(); i++) {
            JsonNode item = node.get(i);
            result[i] = item == null || item.isNull() ? null : element.apply(item);
        }
        return result;
    }

    private static JsonNode requireArray(JsonNode node, String path) {
        if (!node.isArray()) {
            throw conversionError(path, "array", node);
        }
        return node;
    }

    private static boolean[] toBooleanArray(JsonNode node, String path) {
        JsonNode array = requireArray(node, path);
        boolean[] result = new boolean[array.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = requireBoolean(array.get(i), path + "[" + i + "]");
        }
        return result;
    }

    private static byte[] toByteArray(JsonNode node, String path) {
        JsonNode array = requireArray(node, path);
        byte[] result = new byte[array.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = requireDecimal(array.get(i), path + "[" + i + "]").byteValueExact();
        }
        return result;
    }

    private static short[] toShortArray(JsonNode node, String path) {
        JsonNode array = requireArray(node, path);
        short[] result = new short[array.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = requireDecimal(array.get(i), path + "[" + i + "]").shortValueExact();
        }
        return result;
    }

    private static int[] toIntArray(JsonNode node, String path) {
        JsonNode array = requireArray(node, path);
        int[] result = new int[array.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = requireDecimal(array.get(i), path + "[" + i + "]").intValueExact();
        }
        return result;
    }

    private static long[] toLongArray(JsonNode node, String path) {
        JsonNode array = requireArray(node, path);
        long[] result = new long[array.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = requireDecimal(array.get(i), path + "[" + i + "]").longValueExact();
        }
        return result;
    }

    private static float[] toFloatArray(JsonNode node, String path) {
        JsonNode array = requireArray(node, path);
        float[] result = new float[array.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = requireDecimal(array.get(i), path + "[" + i + "]").floatValue();
        }
        return result;
    }

    private static double[] toDoubleArray(JsonNode node, String path) {
        JsonNode array = requireArray(node, path);
        double[] result = new double[array.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = requireDecimal(array.get(i), path + "[" + i + "]").doubleValue();
        }
        return result;
    }

    // ------------------------------------------------------------------ errors

    private static ApiException schemaError(String path, String message, Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>(extra);
        details.put("path", path);
        return new ApiException(ErrorCode.SCHEMA_INVALID, message + " (" + path + ")", details);
    }

    private static ApiException conversionError(String path, String expected, JsonNode node) {
        return new ApiException(ErrorCode.VALUE_CONVERSION_FAILED,
                "Cannot convert " + node + " to " + expected + " (" + path + ")",
                Map.of("path", path, "expected", expected));
    }
}
