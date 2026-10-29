package io.github.noproblem2000.tsarevnacache.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converts JSON (from the UI or the REST API) into {@link DynamicDtoRecord}s with correctly typed
 * field values, following the field types of the schema.
 *
 * <p>JSON conventions (also produced by {@link DynamicDtoJsonSerializer} when reading):
 * <ul>
 *   <li>Nested DTOs ({@code NULLABLE_OBJECT}, {@code NULLABLE_LIST}) are JSON objects; their class is
 *       {@code "@classId"} (and {@code "@factoryId"} when it differs) or, when absent, the field's
 *       {@code nestedClassId} / {@code nestedClassName}.</li>
 *   <li>Other nested values: strings and booleans as is, numbers become {@code Long}/{@code Double};
 *       an exact Java type can be given as {@code {"@javaType": "java.lang.Integer", "value": 5}}.</li>
 *   <li>Longs may be JSON numbers or strings (large ids survive the browser's number precision);
 *       dates are ISO-8601 strings or epoch milliseconds; BigDecimals are strings or numbers;
 *       byte arrays are Base64 strings or arrays of numbers.</li>
 * </ul>
 */
public final class DynamicDtoConverter {

    public static final String CLASS_ID = "@classId";
    public static final String FACTORY_ID = "@factoryId";
    public static final String JAVA_TYPE = "@javaType";
    public static final String VALUE = "value";

    private static final ObjectMapper PLAIN = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private DynamicDtoConverter() {
    }

    /**
     * Builds a record of {@code schema} from a JSON object.
     *
     * @throws ApiException with {@link ErrorCode#VALUE_CONVERSION_FAILED}, {@link ErrorCode#DTO_SCHEMA_INVALID}
     *                      or {@link ErrorCode#DTO_SCHEMA_NOT_FOUND} for unusable input
     */
    public static DynamicDtoRecord fromJson(JsonNode json, DtoClassSchema schema, DtoSchemaRegistryService registry) {
        return fromJson(json, schema, registry, "$");
    }

    private static DynamicDtoRecord fromJson(JsonNode json, DtoClassSchema schema, DtoSchemaRegistryService registry,
                                             String path) {
        if (json == null || !json.isObject()) {
            throw conversion(path, "a JSON object for " + schema.className());
        }
        Set<String> known = new HashSet<>();
        Map<String, Object> values = new LinkedHashMap<>();
        for (DtoFieldSchema field : schema.fields()) {
            known.add(field.fieldName());
            String fieldPath = path + "." + field.fieldName();
            values.put(field.fieldName(), fromJsonValue(json.get(field.fieldName()), field, schema.factoryId(), registry, fieldPath));
        }
        for (Iterator<String> names = json.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!known.contains(name) && !name.startsWith("@")) {
                throw new ApiException(ErrorCode.DTO_SCHEMA_INVALID, "Field '" + name + "' is not part of "
                        + schema.className() + " (" + path + ")", Map.of("path", path + "." + name));
            }
        }
        return new DynamicDtoRecord(schema, values, registry);
    }

    /**
     * Brings a value stored in a record into the Java type its field is written with; JSON nodes,
     * maps and loosely typed numbers are converted, correctly typed values pass through.
     */
    public static Object toWritable(Object value, DtoFieldSchema field, int factoryId, DtoSchemaRegistryService registry) {
        if (value == null || hasWritableType(value, field.fieldType())) {
            return value;
        }
        JsonNode node = value instanceof JsonNode json ? json : PLAIN.valueToTree(value);
        return fromJsonValue(node, field, factoryId, registry, field.fieldName());
    }

    private static boolean hasWritableType(Object value, DtoFieldType type) {
        return switch (type) {
            case NULLABLE_LONG, RAW_LONG -> value instanceof Long;
            case NULLABLE_STRING, RAW_STRING -> value instanceof String;
            case NULLABLE_DATE -> value instanceof Date;
            case NULLABLE_BOOLEAN, RAW_BOOLEAN -> value instanceof Boolean;
            case NULLABLE_INT, RAW_INT -> value instanceof Integer;
            case NULLABLE_DOUBLE -> value instanceof Double;
            case NULLABLE_BIG_DECIMAL -> value instanceof BigDecimal;
            case NULLABLE_OBJECT -> !(value instanceof Map<?, ?>) && !(value instanceof JsonNode);
            case NULLABLE_LIST -> value instanceof List<?> list
                    && list.stream().noneMatch(element -> element instanceof Map<?, ?> || element instanceof JsonNode);
            case NULLABLE_LONG_LIST -> value instanceof List<?> list && list.stream().allMatch(e -> e == null || e instanceof Long);
            case NULLABLE_INTEGER_LIST -> value instanceof List<?> list && list.stream().allMatch(e -> e == null || e instanceof Integer);
            case RAW_BYTE_ARRAY -> value instanceof byte[];
        };
    }

    private static Object fromJsonValue(JsonNode node, DtoFieldSchema field, int factoryId,
                                        DtoSchemaRegistryService registry, String path) {
        boolean missing = node == null || node.isNull() || node.isMissingNode();
        DtoFieldType type = field.fieldType();
        if (missing) {
            if (type == DtoFieldType.RAW_INT || type == DtoFieldType.RAW_LONG || type == DtoFieldType.RAW_BOOLEAN) {
                throw new ApiException(ErrorCode.VALUE_REQUIRED, "Field " + path + " (" + type + ") cannot be null",
                        Map.of("path", path));
            }
            return null;
        }
        try {
            return switch (type) {
                case NULLABLE_LONG, RAW_LONG -> toLong(node, path);
                case NULLABLE_STRING, RAW_STRING -> toText(node, path);
                case NULLABLE_DATE -> toDate(node, path);
                case NULLABLE_BOOLEAN, RAW_BOOLEAN -> toBoolean(node, path);
                case NULLABLE_INT, RAW_INT -> toInt(node, path);
                case NULLABLE_DOUBLE -> toDecimal(node, path).doubleValue();
                case NULLABLE_BIG_DECIMAL -> toDecimal(node, path);
                case NULLABLE_OBJECT -> nested(node, field, factoryId, registry, path);
                case NULLABLE_LIST -> {
                    List<Object> list = new ArrayList<>();
                    for (int i = 0; i < requireArray(node, path).size(); i++) {
                        list.add(nested(node.get(i), field, factoryId, registry, path + "[" + i + "]"));
                    }
                    yield list;
                }
                case NULLABLE_LONG_LIST -> {
                    List<Long> list = new ArrayList<>();
                    for (int i = 0; i < requireArray(node, path).size(); i++) {
                        JsonNode element = node.get(i);
                        list.add(element.isNull() ? null : toLong(element, path + "[" + i + "]"));
                    }
                    yield list;
                }
                case NULLABLE_INTEGER_LIST -> {
                    List<Integer> list = new ArrayList<>();
                    for (int i = 0; i < requireArray(node, path).size(); i++) {
                        JsonNode element = node.get(i);
                        Integer value = element.isNull() ? null : toInt(element, path + "[" + i + "]");
                        if (value != null && value == -1) {
                            throw new ApiException(ErrorCode.VALUE_CONVERSION_FAILED, "-1 is reserved for null elements in "
                                    + path + "[" + i + "] (NULLABLE_INTEGER_LIST)", Map.of("path", path + "[" + i + "]"));
                        }
                        list.add(value);
                    }
                    yield list;
                }
                case RAW_BYTE_ARRAY -> toBytes(node, path);
            };
        } catch (ArithmeticException | IllegalArgumentException e) {
            throw conversion(path, type + " (" + e.getMessage() + ")");
        }
    }

    /** Value of a NULLABLE_OBJECT field or of a NULLABLE_LIST element. */
    private static Object nested(JsonNode node, DtoFieldSchema field, int parentFactoryId,
                                 DtoSchemaRegistryService registry, String path) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject() && node.has(JAVA_TYPE)) {
            return typedScalar(node.get(JAVA_TYPE).asText(), node.get(VALUE), path);
        }
        if (node.isObject()) {
            int factoryId = node.hasNonNull(FACTORY_ID) ? node.get(FACTORY_ID).asInt() : parentFactoryId;
            Integer classId = node.hasNonNull(CLASS_ID) ? Integer.valueOf(node.get(CLASS_ID).asInt())
                    : field.nestedClassId() != null ? field.nestedClassId()
                    : registry.resolveClassId(field.nestedClassName()).orElse(null);
            if (classId == null) {
                throw new ApiException(ErrorCode.DTO_SCHEMA_INVALID, "Cannot tell which DTO " + path + " is: add \""
                        + CLASS_ID + "\" to the object or set nestedClassId of field '" + field.fieldName() + "'",
                        Map.of("path", path));
            }
            DtoClassSchema schema = registry.find(factoryId, classId).orElseThrow(() -> new ApiException(
                    ErrorCode.DTO_SCHEMA_NOT_FOUND, "No DTO schema for factoryId=" + factoryId + ", classId=" + classId
                    + " (" + path + ")", Map.of("factoryId", factoryId, "classId", classId, "path", path)));
            return fromJson(node, schema, registry, path);
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < node.size(); i++) {
                list.add(nested(node.get(i), field, parentFactoryId, registry, path + "[" + i + "]"));
            }
            return list;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.canConvertToLong() ? Long.valueOf(node.longValue()) : node.bigIntegerValue();
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        throw conversion(path, "a nested value");
    }

    private static Object typedScalar(String javaType, JsonNode value, String path) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return switch (javaType) {
                case "java.lang.String" -> value.asText();
                case "java.lang.Long" -> toLong(value, path);
                case "java.lang.Integer" -> toInt(value, path);
                case "java.lang.Short" -> toDecimal(value, path).shortValueExact();
                case "java.lang.Byte" -> toDecimal(value, path).byteValueExact();
                case "java.lang.Double" -> toDecimal(value, path).doubleValue();
                case "java.lang.Float" -> toDecimal(value, path).floatValue();
                case "java.lang.Boolean" -> toBoolean(value, path);
                case "java.math.BigDecimal" -> toDecimal(value, path);
                case "java.math.BigInteger" -> toDecimal(value, path).toBigIntegerExact();
                case "java.util.Date" -> toDate(value, path);
                case "java.util.UUID" -> UUID.fromString(value.asText());
                default -> throw new ApiException(ErrorCode.VALUE_CONVERSION_FAILED, "Unsupported " + JAVA_TYPE + " '"
                        + javaType + "' at " + path, Map.of("path", path, "javaType", javaType));
            };
        } catch (ArithmeticException | IllegalArgumentException e) {
            throw conversion(path, javaType + " (" + e.getMessage() + ")");
        }
    }

    // ------------------------------------------------------------------ scalar conversions

    private static Long toLong(JsonNode node, String path) {
        if (node.isIntegralNumber() || node.isTextual()) {
            return toDecimal(node, path).longValueExact();
        }
        throw conversion(path, "a whole number");
    }

    private static Integer toInt(JsonNode node, String path) {
        if (node.isIntegralNumber() || node.isTextual()) {
            return toDecimal(node, path).intValueExact();
        }
        throw conversion(path, "a whole number");
    }

    private static BigDecimal toDecimal(JsonNode node, String path) {
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            return new BigDecimal(node.textValue().trim());
        }
        throw conversion(path, "a number");
    }

    private static Boolean toBoolean(JsonNode node, String path) {
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual() && ("true".equalsIgnoreCase(node.textValue()) || "false".equalsIgnoreCase(node.textValue()))) {
            return Boolean.parseBoolean(node.textValue());
        }
        throw conversion(path, "true or false");
    }

    private static String toText(JsonNode node, String path) {
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        throw conversion(path, "a string");
    }

    private static Date toDate(JsonNode node, String path) {
        if (node.isIntegralNumber()) {
            return new Date(node.longValue());
        }
        if (!node.isTextual()) {
            throw conversion(path, "an ISO-8601 date or epoch milliseconds");
        }
        String text = node.textValue().trim();
        if (text.matches("-?\\d+")) {
            return new Date(Long.parseLong(text));
        }
        try {
            return Date.from(OffsetDateTime.parse(text).toInstant());
        } catch (RuntimeException notOffset) {
            try {
                return Date.from(Instant.parse(text));
            } catch (RuntimeException notInstant) {
                try {
                    return Date.from(LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant());
                } catch (RuntimeException notLocalDateTime) {
                    try {
                        return Date.from(LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant());
                    } catch (RuntimeException notDate) {
                        throw conversion(path, "an ISO-8601 date such as 2026-01-31T12:00:00Z");
                    }
                }
            }
        }
    }

    private static byte[] toBytes(JsonNode node, String path) {
        if (node.isTextual()) {
            return Base64.getDecoder().decode(node.textValue());
        }
        if (node.isArray()) {
            byte[] bytes = new byte[node.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = toDecimal(node.get(i), path + "[" + i + "]").byteValueExact();
            }
            return bytes;
        }
        throw conversion(path, "a Base64 string or an array of bytes");
    }

    private static JsonNode requireArray(JsonNode node, String path) {
        if (!node.isArray()) {
            throw conversion(path, "an array");
        }
        return node;
    }

    private static ApiException conversion(String path, String expected) {
        return new ApiException(ErrorCode.VALUE_CONVERSION_FAILED, "Expected " + expected + " at " + path,
                Map.of("path", path, "expected", expected));
    }

    /** Whether a long can be represented exactly by a JavaScript number (used by the JSON serializer). */
    static boolean isSafeJsonInteger(long value) {
        return Math.abs(value) <= 9_007_199_254_740_991L;
    }
}
