package io.github.noproblem2000.tsarevnacache.inspector;

import com.hazelcast.internal.serialization.impl.compact.CompactGenericRecord;
import com.hazelcast.internal.serialization.impl.portable.PortableGenericRecord;
import com.hazelcast.nio.serialization.ClassDefinition;
import com.hazelcast.nio.serialization.FieldKind;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import io.github.noproblem2000.tsarevnacache.api.dto.RecordFieldDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.RecordSchemaDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.RecordSchemaDTO.RecordFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Reads {@link GenericRecord}s (Compact and Portable) without their Java classes.
 */
final class GenericRecordSupport {

    private GenericRecordSupport() {
    }

    static boolean isCompact(GenericRecord record) {
        return record instanceof CompactGenericRecord;
    }

    /**
     * Type label: the Compact type name or {@code portable:<factoryId>:<classId>}.
     */
    static String typeName(GenericRecord record) {
        if (record instanceof CompactGenericRecord compact) {
            return compact.getSchema().getTypeName();
        }
        if (record instanceof PortableGenericRecord portable) {
            ClassDefinition definition = portable.getClassDefinition();
            return "portable:" + definition.getFactoryId() + ":" + definition.getClassId();
        }
        return record.getClass().getName();
    }

    /**
     * Describes the record structure. Nested schemas are taken from nested values, so they are
     * {@code null} when the nested value (or every array element) is null.
     */
    static RecordSchemaDTO describe(GenericRecord record) {
        List<RecordFieldDTO> fields = new ArrayList<>();
        for (String name : record.getFieldNames()) {
            FieldKind kind = record.getFieldKind(name);
            fields.add(new RecordFieldDTO(name, kind, nestedSchema(record, name, kind)));
        }
        if (record instanceof PortableGenericRecord portable) {
            ClassDefinition definition = portable.getClassDefinition();
            return new RecordSchemaDTO(RecordFormat.PORTABLE, typeName(record), definition.getFactoryId(),
                    definition.getClassId(), definition.getVersion(), fields);
        }
        return new RecordSchemaDTO(RecordFormat.COMPACT, typeName(record), null, null, null, fields);
    }

    private static RecordSchemaDTO nestedSchema(GenericRecord record, String name, FieldKind kind) {
        return switch (kind) {
            case COMPACT, PORTABLE -> {
                GenericRecord nested = record.getGenericRecord(name);
                yield nested == null ? null : describe(nested);
            }
            case ARRAY_OF_COMPACT, ARRAY_OF_PORTABLE -> {
                GenericRecord[] nested = record.getArrayOfGenericRecord(name);
                yield nested == null ? null : Stream.of(nested)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .map(GenericRecordSupport::describe)
                        .orElse(null);
            }
            default -> null;
        };
    }

    /**
     * Reads a field as a plain Java value. {@code ARRAY_OF_INT8} is widened to {@code short[]} so it
     * is rendered as a number array instead of Base64.
     */
    static Object readField(GenericRecord record, String name, FieldKind kind) {
        return switch (kind) {
            case NOT_AVAILABLE -> null;
            case BOOLEAN -> record.getBoolean(name);
            case ARRAY_OF_BOOLEAN -> record.getArrayOfBoolean(name);
            case INT8 -> record.getInt8(name);
            case ARRAY_OF_INT8 -> widen(record.getArrayOfInt8(name));
            case CHAR -> String.valueOf(record.getChar(name));
            case ARRAY_OF_CHAR -> {
                char[] chars = record.getArrayOfChar(name);
                yield chars == null ? null : new String(chars);
            }
            case INT16 -> record.getInt16(name);
            case ARRAY_OF_INT16 -> record.getArrayOfInt16(name);
            case INT32 -> record.getInt32(name);
            case ARRAY_OF_INT32 -> record.getArrayOfInt32(name);
            case INT64 -> record.getInt64(name);
            case ARRAY_OF_INT64 -> record.getArrayOfInt64(name);
            case FLOAT32 -> record.getFloat32(name);
            case ARRAY_OF_FLOAT32 -> record.getArrayOfFloat32(name);
            case FLOAT64 -> record.getFloat64(name);
            case ARRAY_OF_FLOAT64 -> record.getArrayOfFloat64(name);
            case STRING -> record.getString(name);
            case ARRAY_OF_STRING -> record.getArrayOfString(name);
            case DECIMAL -> record.getDecimal(name);
            case ARRAY_OF_DECIMAL -> record.getArrayOfDecimal(name);
            case TIME -> record.getTime(name);
            case ARRAY_OF_TIME -> record.getArrayOfTime(name);
            case DATE -> record.getDate(name);
            case ARRAY_OF_DATE -> record.getArrayOfDate(name);
            case TIMESTAMP -> record.getTimestamp(name);
            case ARRAY_OF_TIMESTAMP -> record.getArrayOfTimestamp(name);
            case TIMESTAMP_WITH_TIMEZONE -> record.getTimestampWithTimezone(name);
            case ARRAY_OF_TIMESTAMP_WITH_TIMEZONE -> record.getArrayOfTimestampWithTimezone(name);
            case COMPACT, PORTABLE -> record.getGenericRecord(name);
            case ARRAY_OF_COMPACT, ARRAY_OF_PORTABLE -> record.getArrayOfGenericRecord(name);
            case NULLABLE_BOOLEAN -> record.getNullableBoolean(name);
            case ARRAY_OF_NULLABLE_BOOLEAN -> record.getArrayOfNullableBoolean(name);
            case NULLABLE_INT8 -> record.getNullableInt8(name);
            case ARRAY_OF_NULLABLE_INT8 -> record.getArrayOfNullableInt8(name);
            case NULLABLE_INT16 -> record.getNullableInt16(name);
            case ARRAY_OF_NULLABLE_INT16 -> record.getArrayOfNullableInt16(name);
            case NULLABLE_INT32 -> record.getNullableInt32(name);
            case ARRAY_OF_NULLABLE_INT32 -> record.getArrayOfNullableInt32(name);
            case NULLABLE_INT64 -> record.getNullableInt64(name);
            case ARRAY_OF_NULLABLE_INT64 -> record.getArrayOfNullableInt64(name);
            case NULLABLE_FLOAT32 -> record.getNullableFloat32(name);
            case ARRAY_OF_NULLABLE_FLOAT32 -> record.getArrayOfNullableFloat32(name);
            case NULLABLE_FLOAT64 -> record.getNullableFloat64(name);
            case ARRAY_OF_NULLABLE_FLOAT64 -> record.getArrayOfNullableFloat64(name);
        };
    }

    private static short[] widen(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        short[] result = new short[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = bytes[i];
        }
        return result;
    }
}
