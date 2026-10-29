package io.github.noproblem2000.tsarevnacache.inspector;

import static com.hazelcast.internal.serialization.impl.SerializationConstants.CONSTANT_TYPE_DATA_SERIALIZABLE;
import static com.hazelcast.internal.serialization.impl.SerializationConstants.CONSTANT_TYPE_PORTABLE;
import static com.hazelcast.internal.serialization.impl.SerializationConstants.JAVASCRIPT_JSON_SERIALIZATION_TYPE;
import static com.hazelcast.internal.serialization.impl.SerializationConstants.JAVA_DEFAULT_TYPE_EXTERNALIZABLE;
import static com.hazelcast.internal.serialization.impl.SerializationConstants.JAVA_DEFAULT_TYPE_SERIALIZABLE;
import static com.hazelcast.internal.serialization.impl.SerializationConstants.TYPE_COMPACT;
import static com.hazelcast.internal.serialization.impl.SerializationConstants.TYPE_COMPACT_WITH_SCHEMA;

import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.serialization.impl.HeapData;
import io.github.noproblem2000.tsarevnacache.api.dto.DeserializationErrorCode;
import io.github.noproblem2000.tsarevnacache.api.dto.UnresolvedObjectDTO;
import io.github.noproblem2000.tsarevnacache.dto.DtoFieldReadException;
import io.github.noproblem2000.tsarevnacache.dto.DtoSchemaMissingException;
import io.github.noproblem2000.tsarevnacache.dto.DtoSchemaRegistryService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * Explains why a binary could not be deserialized by inspecting the failure and the serialized
 * header (factory/class ids, embedded class names, schema id). Header parsing is best effort and
 * never throws.
 */
final class UnresolvedDataAnalyzer {

    private static final int DS_HEADER_IDENTIFIED = 1;
    private static final int JAVA_STREAM_MAGIC = 0xACED;
    private static final byte TC_CLASSDESC = 0x72;
    private static final int GZIP_MAGIC_FIRST = 0x1F;
    private static final int GZIP_MAGIC_SECOND = 0x8B;
    private static final int MAX_CLASS_NAME_LENGTH = 1024;
    private static final int MAX_STREAM_SCAN = 64;

    /**
     * Hazelcast 5.5 messages naming a nested IdentifiedDataSerializable that could not be created:
     * {@code "<factory> is not be able to create an instance for ID: 2 on factory ID: 1"} (class id
     * first) and {@code "Problem while reading DataSerializable, namespace: 1, ID: 2, ..."}.
     */
    private static final Pattern CREATE_FAILURE = Pattern.compile(
            "create an instance for ID: (-?\\d+) on factory ID: (-?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern READ_FAILURE = Pattern.compile(
            "reading DataSerializable, namespace: (-?\\d+), ID: (-?\\d+)", Pattern.CASE_INSENSITIVE);

    private final DtoSchemaRegistryService dtoSchemas;

    /**
     * @param dtoSchemas dynamic DTO registry used to recognize missing schemas, may be {@code null}
     */
    UnresolvedDataAnalyzer(DtoSchemaRegistryService dtoSchemas) {
        this.dtoSchemas = dtoSchemas;
    }

    /**
     * Factory id / class id of a missing dynamic DTO schema: the top-level object (from the binary
     * header) or a nested one (from Hazelcast's error message), or {@code null}.
     */
    private int[] missingDtoSchema(int typeId, Map<String, Object> metadata, String messages) {
        if (dtoSchemas == null || typeId != CONSTANT_TYPE_DATA_SERIALIZABLE) {
            return null;
        }
        if (metadata.get("factoryId") instanceof Integer factoryId && metadata.get("classId") instanceof Integer classId
                && dtoSchemas.isActiveFactory(factoryId) && dtoSchemas.find(factoryId, classId).isEmpty()) {
            return new int[] {factoryId, classId};
        }
        int[] missing = missingIn(CREATE_FAILURE.matcher(messages), 2, 1);
        return missing != null ? missing : missingIn(READ_FAILURE.matcher(messages), 1, 2);
    }

    private int[] missingIn(Matcher matcher, int factoryGroup, int classGroup) {
        while (matcher.find()) {
            int factoryId = Integer.parseInt(matcher.group(factoryGroup));
            int classId = Integer.parseInt(matcher.group(classGroup));
            if (dtoSchemas.isActiveFactory(factoryId) && dtoSchemas.find(factoryId, classId).isEmpty()) {
                return new int[] {factoryId, classId};
            }
        }
        return null;
    }

    UnresolvedObjectDTO analyze(Data data, Throwable error) {
        int typeId = data.getType();
        Map<String, Object> metadata = new LinkedHashMap<>();
        String headerClass = readHeader(data, typeId, metadata);

        ClassNotFoundException notFound = find(error, ClassNotFoundException.class);
        NoClassDefFoundError noClassDef = find(error, NoClassDefFoundError.class);
        InvalidClassException invalidClass = find(error, InvalidClassException.class);
        String messages = allMessages(error);

        DtoSchemaMissingException missingSchema = find(error, DtoSchemaMissingException.class);
        List<DtoFieldReadException> fieldPath = all(error, DtoFieldReadException.class);
        if (missingSchema != null || !fieldPath.isEmpty()) {
            return analyzeDynamicDto(data, error, typeId, metadata, missingSchema, fieldPath);
        }

        DeserializationErrorCode code;
        String missingClass = null;
        if (notFound != null || noClassDef != null) {
            code = DeserializationErrorCode.CLASS_NOT_FOUND;
            missingClass = notFound != null ? notFound.getMessage() : noClassDef.getMessage();
        } else if (invalidClass != null || messages.contains("invalidclassexception")) {
            code = DeserializationErrorCode.INCOMPATIBLE_CLASS;
            missingClass = invalidClass != null ? invalidClass.classname : null;
        } else if (messages.contains("classnotfoundexception") || messages.contains("noclassdeffounderror")) {
            // Some serializers (e.g. Externalizable) only keep the cause in the message text
            code = DeserializationErrorCode.CLASS_NOT_FOUND;
        } else if (typeId == CONSTANT_TYPE_PORTABLE
                && (messages.contains("factory") || messages.contains("class-definition")
                || messages.contains("class definition"))) {
            code = DeserializationErrorCode.PORTABLE_FACTORY_MISSING;
        } else if (missingDtoSchema(typeId, metadata, messages) != null) {
            int[] missing = missingDtoSchema(typeId, metadata, messages);
            code = DeserializationErrorCode.DTO_SCHEMA_MISSING;
            metadata.put("missingFactoryId", missing[0]);
            metadata.put("missingClassId", missing[1]);
            missingClass = dtoSchemas.classNameFor(missing[0], missing[1])
                    .map(name -> name + " (factoryId=" + missing[0] + ", classId=" + missing[1] + ")")
                    .orElse("factoryId=" + missing[0] + ", classId=" + missing[1]);
        } else if (typeId == CONSTANT_TYPE_DATA_SERIALIZABLE && messages.contains("factory")) {
            code = DeserializationErrorCode.DATA_SERIALIZABLE_FACTORY_MISSING;
        } else if (typeId > 0) {
            code = DeserializationErrorCode.CUSTOM_SERIALIZER_MISSING;
        } else if ((typeId == TYPE_COMPACT || typeId == TYPE_COMPACT_WITH_SCHEMA) && messages.contains("schema")) {
            code = DeserializationErrorCode.COMPACT_SCHEMA_UNAVAILABLE;
        } else {
            code = DeserializationErrorCode.DESERIALIZATION_ERROR;
        }
        if (missingClass == null) {
            missingClass = headerClass;
        }
        if (missingClass != null) {
            missingClass = missingClass.replace('/', '.');
        }

        return UnresolvedObjectDTO.builder()
                .errorCode(code)
                .reason(describe(error))
                .missingClass(missingClass)
                .typeId(typeId)
                .serializer(serializerName(typeId))
                .serializedSize(data.totalSize())
                .metadata(metadata)
                .build();
    }

    /**
     * Failure inside the dynamic DTO engine, explained from the exact exceptions it threw instead of
     * the top-level header (which names the root DTO even when a nested one is the problem):
     * <ul>
     *   <li>a {@link DtoSchemaMissingException} in the chain: the class it names (root or nested)
     *       has no schema - {@code DTO_SCHEMA_MISSING} with that factory id / class id;</li>
     *   <li>otherwise the innermost {@link DtoFieldReadException}: all schemas exist, but the
     *       layout of that class does not match the bytes - {@code DTO_FIELD_READ_ERROR}.</li>
     * </ul>
     */
    private UnresolvedObjectDTO analyzeDynamicDto(Data data, Throwable error, int typeId, Map<String, Object> metadata,
                                                  DtoSchemaMissingException missingSchema,
                                                  List<DtoFieldReadException> fieldPath) {
        Integer rootFactoryId = metadata.get("factoryId") instanceof Integer id ? id : null;
        Integer rootClassId = metadata.get("classId") instanceof Integer id ? id : null;
        if (rootFactoryId == null && !fieldPath.isEmpty()) {
            rootFactoryId = fieldPath.get(0).factoryId();
            rootClassId = fieldPath.get(0).classId();
        }
        if (rootFactoryId != null && rootClassId != null) {
            metadata.put("rootFactoryId", rootFactoryId);
            metadata.put("rootClassId", rootClassId);
            className(rootFactoryId, rootClassId).ifPresent(name -> metadata.put("rootClassName", name));
        }
        if (!fieldPath.isEmpty()) {
            metadata.put("fieldPath", fieldPath.stream().map(DtoFieldReadException::fieldPath).collect(Collectors.joining(" > ")));
        }

        DeserializationErrorCode code;
        String missingClass = null;
        String reason;
        if (missingSchema != null) {
            code = DeserializationErrorCode.DTO_SCHEMA_MISSING;
            int factoryId = missingSchema.factoryId();
            int classId = missingSchema.classId();
            String name = missingSchema.className() != null ? missingSchema.className() : className(factoryId, classId).orElse(null);
            metadata.put("missingFactoryId", factoryId);
            metadata.put("missingClassId", classId);
            if (name != null) {
                metadata.put("missingClassName", name);
            }
            metadata.put("nested", rootFactoryId != null && rootClassId != null
                    && (rootFactoryId != factoryId || rootClassId != classId));
            missingClass = (name == null ? "" : name + " ") + "(factoryId=" + factoryId + ", classId=" + classId + ")";
            reason = fieldPath.isEmpty() ? missingSchema.getMessage() : fieldPath.get(0).getMessage();
        } else {
            code = DeserializationErrorCode.DTO_FIELD_READ_ERROR;
            DtoFieldReadException failed = fieldPath.get(fieldPath.size() - 1);
            metadata.put("failedFactoryId", failed.factoryId());
            metadata.put("failedClassId", failed.classId());
            metadata.put("failedClassName", failed.className());
            metadata.put("fieldOrder", failed.fieldOrder());
            metadata.put("fieldName", failed.fieldName());
            metadata.put("fieldType", failed.fieldType().name());
            metadata.put("fieldError", failed.originalMessage());
            reason = failed.getMessage();
        }
        return UnresolvedObjectDTO.builder()
                .errorCode(code)
                .reason(reason == null ? describe(error) : reason)
                .missingClass(missingClass)
                .typeId(typeId)
                .serializer(serializerName(typeId))
                .serializedSize(data.totalSize())
                .metadata(metadata)
                .build();
    }

    private Optional<String> className(int factoryId, int classId) {
        return dtoSchemas == null ? Optional.empty() : dtoSchemas.classNameFor(factoryId, classId);
    }

    /** Human readable name of the serializer family that produced a binary. */
    static String serializerName(int typeId) {
        if (typeId > 0) {
            return "Custom serializer (type id " + typeId + ")";
        }
        return switch (typeId) {
            case CONSTANT_TYPE_PORTABLE -> "Portable";
            case CONSTANT_TYPE_DATA_SERIALIZABLE -> "DataSerializable";
            case JAVA_DEFAULT_TYPE_SERIALIZABLE -> "Java Serializable";
            case JAVA_DEFAULT_TYPE_EXTERNALIZABLE -> "Java Externalizable";
            case TYPE_COMPACT, TYPE_COMPACT_WITH_SCHEMA -> "Compact";
            case JAVASCRIPT_JSON_SERIALIZATION_TYPE -> "HazelcastJsonValue";
            default -> typeId <= -300 && typeId >= -399 ? "Jet internal"
                    : typeId <= -200 && typeId >= -206 ? "Hibernate"
                    : "Built-in (type id " + typeId + ")";
        };
    }

    // ------------------------------------------------------------------ header parsing

    private static String readHeader(Data data, int typeId, Map<String, Object> metadata) {
        try {
            byte[] bytes = data.toByteArray();
            if (bytes == null || bytes.length <= HeapData.DATA_OFFSET) {
                return null;
            }
            ByteBuffer payload = ByteBuffer.wrap(bytes, HeapData.DATA_OFFSET, bytes.length - HeapData.DATA_OFFSET);
            return switch (typeId) {
                case CONSTANT_TYPE_PORTABLE -> {
                    metadata.put("factoryId", payload.getInt());
                    metadata.put("classId", payload.getInt());
                    metadata.put("version", payload.getInt());
                    yield "Portable(factoryId=" + metadata.get("factoryId") + ", classId=" + metadata.get("classId") + ")";
                }
                case CONSTANT_TYPE_DATA_SERIALIZABLE -> readDataSerializableHeader(payload, metadata);
                case JAVA_DEFAULT_TYPE_EXTERNALIZABLE -> {
                    String className = readString(payload);
                    if (className != null) {
                        metadata.put("className", className);
                    }
                    yield className;
                }
                case JAVA_DEFAULT_TYPE_SERIALIZABLE -> {
                    String className = readJavaStreamClassName(bytes, HeapData.DATA_OFFSET);
                    if (className != null) {
                        metadata.put("className", className);
                    }
                    yield className;
                }
                case TYPE_COMPACT, TYPE_COMPACT_WITH_SCHEMA -> {
                    metadata.put("schemaId", payload.getLong());
                    yield null;
                }
                default -> null;
            };
        } catch (RuntimeException | IOException e) {
            return null;
        }
    }

    private static String readDataSerializableHeader(ByteBuffer payload, Map<String, Object> metadata) {
        byte header = payload.get();
        boolean identified = (header & DS_HEADER_IDENTIFIED) != 0;
        metadata.put("identified", identified);
        if (identified) {
            int factoryId = payload.getInt();
            int classId = payload.getInt();
            metadata.put("factoryId", factoryId);
            metadata.put("classId", classId);
            return "IdentifiedDataSerializable(factoryId=" + factoryId + ", classId=" + classId + ")";
        }
        String className = readString(payload);
        if (className != null) {
            metadata.put("className", className);
        }
        return className;
    }

    /** Reads a Hazelcast string: int byte length followed by UTF-8 bytes. */
    private static String readString(ByteBuffer buffer) {
        int length = buffer.getInt();
        if (length <= 0 || length > MAX_CLASS_NAME_LENGTH || length > buffer.remaining()) {
            return null;
        }
        byte[] utf8 = new byte[length];
        buffer.get(utf8);
        return new String(utf8, StandardCharsets.UTF_8);
    }

    /**
     * Finds the first class descriptor name of a Java serialization stream inside the payload
     * (optionally GZIP compressed).
     */
    private static String readJavaStreamClassName(byte[] bytes, int offset) throws IOException {
        byte[] stream = bytes;
        int start = offset;
        if (bytes.length > offset + 1
                && (bytes[offset] & 0xFF) == GZIP_MAGIC_FIRST && (bytes[offset + 1] & 0xFF) == GZIP_MAGIC_SECOND) {
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes, offset, bytes.length - offset))) {
                stream = in.readNBytes(MAX_CLASS_NAME_LENGTH * 4);
                start = 0;
            }
        }
        int limit = Math.min(stream.length - 1, start + MAX_STREAM_SCAN);
        for (int i = start; i < limit; i++) {
            if (((stream[i] & 0xFF) << 8 | (stream[i + 1] & 0xFF)) == JAVA_STREAM_MAGIC) {
                return classDescriptorName(ByteBuffer.wrap(stream, i, stream.length - i));
            }
        }
        return null;
    }

    private static String classDescriptorName(ByteBuffer buffer) {
        int begin = buffer.position();
        try {
            buffer.getShort(); // magic
            buffer.getShort(); // version
            while (buffer.hasRemaining()) {
                if (buffer.get() == TC_CLASSDESC) {
                    int length = buffer.getShort() & 0xFFFF;
                    if (length == 0 || length > buffer.remaining()) {
                        return null;
                    }
                    byte[] name = new byte[length];
                    buffer.get(name);
                    return new String(name, StandardCharsets.UTF_8);
                }
                if (buffer.position() - begin > MAX_STREAM_SCAN) {
                    return null;
                }
            }
        } catch (BufferUnderflowException ignored) {
            // malformed stream
        }
        return null;
    }

    // ------------------------------------------------------------------ exception helpers

    private static <T extends Throwable> T find(Throwable error, Class<T> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }

    /** All exceptions of a type in the cause chain, outermost first. */
    private static <T extends Throwable> List<T> all(Throwable error, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                found.add(type.cast(current));
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return found;
    }

    private static String allMessages(Throwable error) {
        StringBuilder text = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            text.append(' ').append(current.getMessage());
            if (current.getCause() == current) {
                break;
            }
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static String describe(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() != null ? root.getMessage() : error.getMessage();
        return root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
