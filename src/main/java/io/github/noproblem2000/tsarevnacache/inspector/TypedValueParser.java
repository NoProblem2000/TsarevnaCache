package io.github.noproblem2000.tsarevnacache.inspector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.hazelcast.core.HazelcastJsonValue;
import io.github.noproblem2000.tsarevnacache.api.dto.DataType;
import io.github.noproblem2000.tsarevnacache.api.dto.TypedValueInput;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.config.DtoClassLoader;
import io.github.noproblem2000.tsarevnacache.dto.DtoClassSchema;
import io.github.noproblem2000.tsarevnacache.dto.DtoSchemaRegistryService;
import io.github.noproblem2000.tsarevnacache.dto.DynamicDtoConverter;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.util.StringUtils;

/**
 * Converts user input ({@link TypedValueInput}) into the Java object that is put into the cluster.
 */
final class TypedValueParser {

    private final ObjectMapper mapper;
    private final DtoClassLoader classLoader;
    private final List<String> deniedClassPrefixes;
    private final ObjenesisStd objenesis = new ObjenesisStd(true);
    private final DtoSchemaRegistryService dtoSchemas;

    TypedValueParser(ObjectMapper mapper, DtoClassLoader classLoader, List<String> deniedClassPrefixes,
                     DtoSchemaRegistryService dtoSchemas) {
        this.mapper = mapper;
        this.classLoader = classLoader;
        this.deniedClassPrefixes = List.copyOf(deniedClassPrefixes);
        this.dtoSchemas = dtoSchemas;
    }

    /** Schema of an IDENTIFIED_DTO input: by class id (and factory id) or by DTO class name. */
    private DtoClassSchema dtoSchema(TypedValueInput input) {
        if (input.classId() != null) {
            int factoryId = input.factoryId() != null ? input.factoryId() : dtoSchemas.factoryId();
            return dtoSchemas.find(factoryId, input.classId()).orElseThrow(() -> new ApiException(
                    ErrorCode.DTO_SCHEMA_NOT_FOUND, "No DTO schema for factoryId=" + factoryId + ", classId=" + input.classId(),
                    Map.of("factoryId", factoryId, "classId", input.classId())));
        }
        if (StringUtils.hasText(input.className())) {
            return dtoSchemas.findByClassName(input.className()).orElseThrow(() -> new ApiException(
                    ErrorCode.DTO_SCHEMA_NOT_FOUND, "No DTO schema for class " + input.className(),
                    Map.of("className", input.className())));
        }
        throw new ApiException(ErrorCode.VALUE_REQUIRED, "IDENTIFIED_DTO values need a classId or a DTO class name");
    }

    Object parse(TypedValueInput input) {
        if (input == null || input.type() == null) {
            throw new ApiException(ErrorCode.VALUE_REQUIRED, "A type is required");
        }
        JsonNode raw = input.raw();
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            throw new ApiException(ErrorCode.VALUE_REQUIRED, "A value is required",
                    Map.of("type", input.type().name()));
        }
        DataType type = input.type();
        try {
            return switch (type) {
                case STRING -> raw.isValueNode() ? raw.asText() : raw.toString();
                case INTEGER -> decimal(raw, type).intValueExact();
                case LONG -> decimal(raw, type).longValueExact();
                case SHORT -> decimal(raw, type).shortValueExact();
                case BYTE -> decimal(raw, type).byteValueExact();
                case DOUBLE -> raw.isNumber() ? raw.doubleValue() : Double.parseDouble(scalar(raw, type));
                case FLOAT -> raw.isNumber() ? raw.floatValue() : Float.parseFloat(scalar(raw, type));
                case BOOLEAN -> bool(raw);
                case UUID -> UUID.fromString(scalar(raw, type));
                case BIG_DECIMAL -> decimal(raw, type);
                case BIG_INTEGER -> decimal(raw, type).toBigIntegerExact();
                case JSON -> new HazelcastJsonValue(json(raw).toString());
                case CLASS -> toClassInstance(json(raw), input.className());
                case COMPACT -> {
                    if (input.schema() == null) {
                        throw new ApiException(ErrorCode.SCHEMA_REQUIRED, "A schema is required for COMPACT values");
                    }
                    yield CompactRecordFactory.build(input.schema(), json(raw));
                }
                case IDENTIFIED_DTO -> DynamicDtoConverter.fromJson(json(raw), dtoSchema(input), dtoSchemas);
            };
        } catch (IllegalArgumentException | ArithmeticException e) {
            // NumberFormatException, UUID.fromString, *ValueExact range checks
            throw conversionError(raw, type, e);
        }
    }

    private Object toClassInstance(JsonNode json, String className) {
        Class<?> type = resolveClass(className);
        try {
            return mapper.treeToValue(json, type);
        } catch (InvalidDefinitionException e) {
            // Typically "no Creators": fall back to allocation without a constructor + field binding.
            if (canAllocateWithoutConstructor(type)) {
                try {
                    Object instance = objenesis.newInstance(type);
                    return mapper.readerForUpdating(instance).readValue(json);
                } catch (IOException | RuntimeException fallbackError) {
                    e.addSuppressed(fallbackError);
                }
            }
            throw instantiationError(className, e);
        } catch (JsonProcessingException e) {
            throw instantiationError(className, e);
        }
    }

    private Class<?> resolveClass(String className) {
        if (!StringUtils.hasText(className)) {
            throw new ApiException(ErrorCode.VALUE_REQUIRED, "A class name is required for CLASS values");
        }
        String name = className.trim();
        for (String prefix : deniedClassPrefixes) {
            if (name.startsWith(prefix)) {
                throw new ApiException(ErrorCode.CLASS_NOT_ALLOWED, "Class " + name + " is in a denied package",
                        Map.of("className", name, "deniedPrefix", prefix));
            }
        }
        Class<?> type;
        try {
            type = classLoader.load(name);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new ApiException(ErrorCode.CLASS_NOT_FOUND, "Class " + name + " is not on the DTO classpath",
                    Map.of("className", name), e);
        }
        if (type.isInterface() || type.isPrimitive() || type.isArray() || type.isAnnotation()) {
            throw new ApiException(ErrorCode.CLASS_INSTANTIATION_FAILED,
                    "Class " + name + " cannot be instantiated", Map.of("className", name));
        }
        return type;
    }

    private static boolean canAllocateWithoutConstructor(Class<?> type) {
        return !Modifier.isAbstract(type.getModifiers()) && !type.isRecord() && !type.isEnum();
    }

    private JsonNode json(JsonNode raw) {
        if (!raw.isTextual()) {
            return raw;
        }
        try {
            return mapper.readTree(raw.textValue());
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INVALID_JSON, "Invalid JSON: " + e.getOriginalMessage(),
                    Map.of("line", e.getLocation() == null ? 0 : e.getLocation().getLineNr(),
                            "column", e.getLocation() == null ? 0 : e.getLocation().getColumnNr()), e);
        }
    }

    private static BigDecimal decimal(JsonNode raw, DataType type) {
        return raw.isNumber() ? raw.decimalValue() : new BigDecimal(scalar(raw, type));
    }

    private static boolean bool(JsonNode raw) {
        if (raw.isBoolean()) {
            return raw.booleanValue();
        }
        String text = scalar(raw, DataType.BOOLEAN);
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException("Not a boolean: " + text);
    }

    private static String scalar(JsonNode raw, DataType type) {
        if (!raw.isValueNode()) {
            throw new IllegalArgumentException("A scalar value is expected for " + type);
        }
        return raw.asText().trim();
    }

    private static ApiException conversionError(JsonNode raw, DataType type, Exception cause) {
        String input = raw.isValueNode() ? raw.asText() : raw.toString();
        if (input.length() > 100) {
            input = input.substring(0, 100) + "...";
        }
        return new ApiException(ErrorCode.VALUE_CONVERSION_FAILED,
                "Cannot convert '" + input + "' to " + type + ": " + cause.getMessage(),
                Map.of("type", type.name(), "input", input), cause);
    }

    private static ApiException instantiationError(String className, Exception cause) {
        String message = cause instanceof JsonProcessingException json ? json.getOriginalMessage() : cause.getMessage();
        return new ApiException(ErrorCode.CLASS_INSTANTIATION_FAILED,
                "Cannot bind JSON to " + className + ": " + message,
                Map.of("className", className, "reason", String.valueOf(message)), cause);
    }
}
