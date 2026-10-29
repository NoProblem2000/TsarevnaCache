package io.github.noproblem2000.tsarevnacache.dto;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.HazelcastSerializationException;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generic stand-in for a DTO class that is not on the classpath: an
 * {@link IdentifiedDataSerializable} whose fields live in a map and whose binary layout comes from
 * the {@link DtoClassSchema} registered for its factory id / class id.
 *
 * <p>{@link #readData} and {@link #writeData} walk the schema fields in order and call the same
 * {@link SerializationUtils} helpers as the real class, so the bytes are identical. Nested DTOs
 * ({@code writeObject}) are read back by Hazelcast through the {@link DynamicDataSerializableFactory}
 * and therefore also become {@code DynamicDtoRecord}s.
 */
public final class DynamicDtoRecord implements IdentifiedDataSerializable {

    private final int factoryId;
    private final int classId;
    private final transient DtoSchemaRegistryService registry;
    private final Map<String, Object> data = new LinkedHashMap<>();
    private transient DtoClassSchema schema;

    public DynamicDtoRecord(int factoryId, int classId, DtoSchemaRegistryService registry) {
        this.factoryId = factoryId;
        this.classId = classId;
        this.registry = registry;
    }

    /** Record of an explicit schema, with its values (already converted to the field types). */
    public DynamicDtoRecord(DtoClassSchema schema, Map<String, Object> values, DtoSchemaRegistryService registry) {
        this(schema.factoryId(), schema.classId(), registry);
        this.schema = schema;
        this.data.putAll(values);
    }

    @Override
    public int getFactoryId() {
        return factoryId;
    }

    @Override
    public int getClassId() {
        return classId;
    }

    /** Field values in schema order; nested DTOs are {@code DynamicDtoRecord}s. */
    public Map<String, Object> data() {
        return Collections.unmodifiableMap(data);
    }

    DtoSchemaRegistryService registry() {
        return registry;
    }

    /** Schema this record was read or built with. */
    public DtoClassSchema schema() {
        if (schema == null) {
            schema = registry.find(factoryId, classId).orElseThrow(() -> new HazelcastSerializationException(
                    "No DTO schema registered for factoryId=" + factoryId + ", classId=" + classId));
        }
        return schema;
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        DtoClassSchema layout = schema();
        data.clear();
        for (DtoFieldSchema field : layout.fields()) {
            try {
                data.put(field.fieldName(), read(field.fieldType(), in));
            } catch (IOException | RuntimeException e) {
                throw new HazelcastSerializationException(position(layout, field, "reading") + ": " + rootMessage(e), e);
            }
        }
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        DtoClassSchema layout = schema();
        for (DtoFieldSchema field : layout.fields()) {
            Object value = DynamicDtoConverter.toWritable(data.get(field.fieldName()), field, factoryId, registry);
            try {
                write(field.fieldType(), out, value);
            } catch (IOException | RuntimeException e) {
                throw new HazelcastSerializationException(position(layout, field, "writing") + ": " + rootMessage(e), e);
            }
        }
    }

    private static Object read(DtoFieldType type, ObjectDataInput in) throws IOException {
        return switch (type) {
            case NULLABLE_LONG -> SerializationUtils.readNullableLong(in);
            case NULLABLE_STRING -> SerializationUtils.readNullableString(in);
            case NULLABLE_DATE -> SerializationUtils.readNullableDate(in);
            case NULLABLE_OBJECT -> SerializationUtils.readNullableObject(in);
            case NULLABLE_BOOLEAN -> SerializationUtils.readNullableBoolean(in);
            case NULLABLE_INT -> SerializationUtils.readNullableInt(in);
            case NULLABLE_DOUBLE -> SerializationUtils.readNullableDouble(in);
            case NULLABLE_BIG_DECIMAL -> SerializationUtils.readNullableBigDecimal(in);
            case NULLABLE_LIST -> SerializationUtils.readNullableList(in);
            case NULLABLE_LONG_LIST -> SerializationUtils.readNullableLongList(in);
            case NULLABLE_INTEGER_LIST -> SerializationUtils.readNullableIntegerList(in);
            case RAW_INT -> SerializationUtils.readRawInt(in);
            case RAW_LONG -> SerializationUtils.readRawLong(in);
            case RAW_BOOLEAN -> SerializationUtils.readRawBoolean(in);
            case RAW_STRING -> SerializationUtils.readRawString(in);
            case RAW_BYTE_ARRAY -> SerializationUtils.readRawByteArray(in);
        };
    }

    @SuppressWarnings("unchecked")
    private static void write(DtoFieldType type, ObjectDataOutput out, Object value) throws IOException {
        switch (type) {
            case NULLABLE_LONG -> SerializationUtils.writeNullableLong(out, (Long) value);
            case NULLABLE_STRING -> SerializationUtils.writeNullableString(out, (String) value);
            case NULLABLE_DATE -> SerializationUtils.writeNullableDate(out, (Date) value);
            case NULLABLE_OBJECT -> SerializationUtils.writeNullableObject(out, value);
            case NULLABLE_BOOLEAN -> SerializationUtils.writeNullableBoolean(out, (Boolean) value);
            case NULLABLE_INT -> SerializationUtils.writeNullableInt(out, (Integer) value);
            case NULLABLE_DOUBLE -> SerializationUtils.writeNullableDouble(out, (Double) value);
            case NULLABLE_BIG_DECIMAL -> SerializationUtils.writeNullableBigDecimal(out, (BigDecimal) value);
            case NULLABLE_LIST -> SerializationUtils.writeNullableList(out, (List<?>) value);
            case NULLABLE_LONG_LIST -> SerializationUtils.writeNullableLongList(out, (List<Long>) value);
            case NULLABLE_INTEGER_LIST -> SerializationUtils.writeNullableIntegerList(out, (List<Integer>) value);
            case RAW_INT -> SerializationUtils.writeRawInt(out, (Integer) value);
            case RAW_LONG -> SerializationUtils.writeRawLong(out, (Long) value);
            case RAW_BOOLEAN -> SerializationUtils.writeRawBoolean(out, (Boolean) value);
            case RAW_STRING -> SerializationUtils.writeRawString(out, (String) value);
            case RAW_BYTE_ARRAY -> SerializationUtils.writeRawByteArray(out, (byte[]) value);
        }
    }

    private static String position(DtoClassSchema layout, DtoFieldSchema field, String action) {
        return "Error " + action + " field #" + field.order() + " '" + field.fieldName() + "' (" + field.fieldType()
                + ") of " + layout.className() + " [factoryId=" + layout.factoryId() + ", classId=" + layout.classId()
                + "] - does the schema match the class version that wrote the data?";
    }

    private static String rootMessage(Throwable error) {
        if (error instanceof HazelcastSerializationException && error.getMessage() != null) {
            // nested DTO: keep its message, it already names the failing nested field
            return error.getMessage();
        }
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : " " + root.getMessage());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DynamicDtoRecord record && record.factoryId == factoryId
                && record.classId == classId && record.data.equals(data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(factoryId, classId, data);
    }

    @Override
    public String toString() {
        return "DynamicDtoRecord[" + factoryId + "/" + classId + " " + data + "]";
    }
}
