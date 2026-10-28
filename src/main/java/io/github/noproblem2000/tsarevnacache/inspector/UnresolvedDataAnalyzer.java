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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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

    UnresolvedObjectDTO analyze(Data data, Throwable error) {
        int typeId = data.getType();
        Map<String, Object> metadata = new LinkedHashMap<>();
        String headerClass = readHeader(data, typeId, metadata);

        ClassNotFoundException notFound = find(error, ClassNotFoundException.class);
        NoClassDefFoundError noClassDef = find(error, NoClassDefFoundError.class);
        InvalidClassException invalidClass = find(error, InvalidClassException.class);
        String messages = allMessages(error);

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
