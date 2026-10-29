package io.github.noproblem2000.tsarevnacache.inspector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.hazelcast.client.HazelcastClientOfflineException;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.core.OperationTimeoutException;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.serialization.InternalSerializationService;
import com.hazelcast.nio.serialization.HazelcastSerializationException;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.spi.exception.RetryableException;
import com.hazelcast.spi.impl.SerializationServiceSupport;
import io.github.noproblem2000.tsarevnacache.api.dto.DataType;
import io.github.noproblem2000.tsarevnacache.api.dto.InspectedValue;
import io.github.noproblem2000.tsarevnacache.api.dto.InspectedValue.InspectionWarning;
import io.github.noproblem2000.tsarevnacache.api.dto.TypedValueInput;
import io.github.noproblem2000.tsarevnacache.api.dto.UnresolvedObjectDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.ValueKind;
import io.github.noproblem2000.tsarevnacache.config.DtoClassLoader;
import io.github.noproblem2000.tsarevnacache.config.properties.InspectorProperties;
import io.github.noproblem2000.tsarevnacache.dto.DtoClassSchema;
import io.github.noproblem2000.tsarevnacache.dto.DtoSchemaRegistryService;
import io.github.noproblem2000.tsarevnacache.dto.DynamicDtoRecord;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Universal converter between cluster objects and JSON.
 *
 * <p><b>Reading</b> works on the serialized form ({@link Data}) through a fallback pipeline:
 * <ol>
 *   <li>Compact/Portable {@link GenericRecord}s (Compact data whose class is not on the classpath
 *       is returned as a record by the client) are rendered field by field, with their schema.</li>
 *   <li>{@link HazelcastJsonValue}, strings, numbers, booleans and UUIDs are returned natively.</li>
 *   <li>Any other object is converted by a field-based Jackson mapper, so DTOs without getters
 *       or {@code Serializable} still render.</li>
 *   <li>If deserialization fails (missing class, factory or serializer), an
 *       {@link UnresolvedObjectDTO} describing the failure and the binary header is returned
 *       instead of an error.</li>
 * </ol>
 *
 * <p><b>Writing</b> converts typed user input ({@link TypedValueInput}) into the object to put:
 * scalars, {@code HazelcastJsonValue}, Jackson-bound DTO classes or Compact records built from a
 * schema.
 */
@Slf4j
@Service
public class HazelcastValueInspectorService {

    private final HazelcastInstance hazelcast;
    private final InspectorProperties properties;
    private final ObjectMapper mapper;
    private final TypedValueParser parser;
    private final UnresolvedDataAnalyzer analyzer;
    private volatile InternalSerializationService serializationService;

    public HazelcastValueInspectorService(HazelcastInstance hazelcastClient,
                                          DtoClassLoader dtoClassLoader,
                                          InspectorProperties properties,
                                          DtoSchemaRegistryService dtoSchemaRegistry) {
        this.hazelcast = hazelcastClient;
        this.properties = properties;
        this.mapper = InspectorObjectMapperFactory.create();
        this.parser = new TypedValueParser(mapper, dtoClassLoader, properties.getDeniedClassPrefixes(), dtoSchemaRegistry);
        this.analyzer = new UnresolvedDataAnalyzer(dtoSchemaRegistry);
    }

    // ------------------------------------------------------------------ reading

    /**
     * Deserializes and inspects a binary. Never fails because of serialization problems; only
     * connectivity errors (e.g. the client is offline while fetching a Compact schema) propagate.
     */
    public InspectedValue inspect(Data data) {
        if (data == null) {
            return inspect((Object) null);
        }
        Object object;
        try {
            object = serializationService().toObject(data);
        } catch (HazelcastSerializationException | LinkageError e) {
            return unresolved(data, e);
        } catch (RuntimeException e) {
            if (isConnectivityProblem(e)) {
                throw e;
            }
            return unresolved(data, e);
        }
        return inspect(object).toBuilder().serializedSize(data.totalSize()).build();
    }

    /**
     * Inspects an already deserialized object.
     */
    public InspectedValue inspect(Object value) {
        if (value instanceof Data data) {
            return inspect(data);
        }
        if (value == null) {
            return resolved(ValueKind.NULL, null, null, NullNode.getInstance()).build();
        }
        if (value instanceof DynamicDtoRecord dto) {
            DtoClassSchema schema = dto.schema();
            return resolved(ValueKind.IDENTIFIED_DTO, schema.className(), DataType.IDENTIFIED_DTO, toJson(value))
                    .metadata(Map.of("factoryId", dto.getFactoryId(), "classId", dto.getClassId(),
                            "className", schema.className()))
                    .build();
        }
        if (value instanceof GenericRecord record) {
            boolean compact = GenericRecordSupport.isCompact(record);
            return resolved(compact ? ValueKind.COMPACT : ValueKind.PORTABLE,
                    GenericRecordSupport.typeName(record),
                    compact ? DataType.COMPACT : null,
                    toJson(value))
                    .schema(GenericRecordSupport.describe(record))
                    .build();
        }
        if (value instanceof HazelcastJsonValue) {
            return resolved(ValueKind.JSON, HazelcastJsonValue.class.getName(), DataType.JSON, toJson(value)).build();
        }
        if (value instanceof String text) {
            return text(ValueKind.STRING, String.class, DataType.STRING, text);
        }
        if (value instanceof UUID uuid) {
            return text(ValueKind.UUID, UUID.class, DataType.UUID, uuid.toString());
        }
        if (value instanceof Character character) {
            return text(ValueKind.STRING, Character.class, null, character.toString());
        }
        if (value instanceof Number || value instanceof Boolean) {
            ValueKind kind = value instanceof Boolean ? ValueKind.BOOLEAN : ValueKind.NUMBER;
            return resolved(kind, value.getClass().getName(), scalarType(value), toJson(value)).build();
        }
        if (value instanceof byte[] bytes) {
            return resolved(ValueKind.BYTES, byte[].class.getName(), null,
                    TextNode.valueOf(Base64.getEncoder().encodeToString(bytes)))
                    .preview("byte[" + bytes.length + "]")
                    .metadata(Map.of("length", bytes.length))
                    .build();
        }
        return objectValue(value);
    }

    // ------------------------------------------------------------------ writing

    /**
     * Converts typed user input into the object to store.
     *
     * @throws io.github.noproblem2000.tsarevnacache.api.error.ApiException if the input does not
     *         match the requested type
     */
    public Object toObject(TypedValueInput input) {
        return parser.parse(input);
    }

    /** Serializes an object with the admin client's serialization service. */
    public Data toData(Object object) {
        return serializationService().toData(object);
    }

    /** Converts typed user input directly into its serialized form (e.g. for key lookups). */
    public Data toData(TypedValueInput input) {
        return toData(toObject(input));
    }

    /** Serialization service of the Hazelcast client. */
    public InternalSerializationService serializationService() {
        InternalSerializationService service = serializationService;
        if (service == null) {
            if (!(hazelcast instanceof SerializationServiceSupport support)) {
                throw new IllegalStateException("Unsupported HazelcastInstance: " + hazelcast.getClass().getName());
            }
            service = (InternalSerializationService) support.getSerializationService();
            serializationService = service;
        }
        return service;
    }

    // ------------------------------------------------------------------ internals

    private InspectedValue objectValue(Object value) {
        Class<?> type = value.getClass();
        ValueKind kind;
        if (value instanceof Temporal || value instanceof Date) {
            kind = ValueKind.DATE_TIME;
        } else if (value instanceof Collection<?>) {
            kind = ValueKind.COLLECTION;
        } else if (value instanceof Map<?, ?>) {
            kind = ValueKind.MAP;
        } else if (type.isArray()) {
            kind = ValueKind.ARRAY;
        } else {
            kind = ValueKind.OBJECT;
        }
        DataType dataType = kind == ValueKind.OBJECT && isWritableClass(type.getName()) ? DataType.CLASS : null;
        try {
            return resolved(kind, type.getName(), dataType, toJson(value)).build();
        } catch (IllegalArgumentException | StackOverflowError e) {
            log.debug("Cannot convert {} to JSON", type.getName(), e);
            String text = safeToString(value);
            return resolved(kind, type.getName(), null, TextNode.valueOf(text))
                    .preview(preview(text))
                    .truncated(text.length() > properties.getPreviewLength())
                    .warning(InspectionWarning.JSON_CONVERSION_FAILED)
                    .build();
        }
    }

    private InspectedValue text(ValueKind kind, Class<?> type, DataType dataType, String text) {
        return resolved(kind, type.getName(), dataType, TextNode.valueOf(text))
                .preview(preview(text))
                .truncated(text.length() > properties.getPreviewLength())
                .build();
    }

    private InspectedValue.InspectedValueBuilder resolved(ValueKind kind, String javaType, DataType dataType,
                                                          JsonNode json) {
        String serialized = writeJson(json);
        return InspectedValue.builder()
                .deserialized(true)
                .kind(kind)
                .javaType(javaType)
                .dataType(dataType)
                .value(json)
                .preview(preview(serialized))
                .truncated(serialized.length() > properties.getPreviewLength());
    }

    private InspectedValue unresolved(Data data, Throwable error) {
        UnresolvedObjectDTO details = analyzer.analyze(data, error);
        log.debug("Unresolved object (type id {}): {}", data.getType(), details.reason());
        return InspectedValue.builder()
                .deserialized(false)
                .kind(ValueKind.UNRESOLVED)
                .javaType(details.missingClass())
                .serializedSize(data.totalSize())
                .unresolved(details)
                .build();
    }

    private JsonNode toJson(Object value) {
        return mapper.valueToTree(value);
    }

    private String writeJson(JsonNode json) {
        try {
            return mapper.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            return json.toString();
        }
    }

    private String preview(String text) {
        int limit = properties.getPreviewLength();
        String singleLine = text.length() > limit * 2 ? text.substring(0, limit * 2) : text;
        singleLine = singleLine.replaceAll("[\\r\\n\\t]+", " ");
        if (singleLine.length() <= limit) {
            return singleLine;
        }
        int end = limit;
        if (Character.isHighSurrogate(singleLine.charAt(end - 1))) {
            end--;
        }
        return singleLine.substring(0, end);
    }

    private boolean isWritableClass(String className) {
        return properties.getDeniedClassPrefixes().stream().noneMatch(className::startsWith);
    }

    private static DataType scalarType(Object value) {
        if (value instanceof Integer) {
            return DataType.INTEGER;
        } else if (value instanceof Long) {
            return DataType.LONG;
        } else if (value instanceof Short) {
            return DataType.SHORT;
        } else if (value instanceof Byte) {
            return DataType.BYTE;
        } else if (value instanceof Double) {
            return DataType.DOUBLE;
        } else if (value instanceof Float) {
            return DataType.FLOAT;
        } else if (value instanceof BigDecimal) {
            return DataType.BIG_DECIMAL;
        } else if (value instanceof BigInteger) {
            return DataType.BIG_INTEGER;
        } else if (value instanceof Boolean) {
            return DataType.BOOLEAN;
        }
        return null;
    }

    private static boolean isConnectivityProblem(RuntimeException e) {
        return e instanceof HazelcastClientOfflineException
                || e instanceof HazelcastInstanceNotActiveException
                || e instanceof OperationTimeoutException
                || e instanceof RetryableException;
    }

    private static String safeToString(Object value) {
        try {
            return String.valueOf(value);
        } catch (RuntimeException | StackOverflowError e) {
            return value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
        }
    }
}
