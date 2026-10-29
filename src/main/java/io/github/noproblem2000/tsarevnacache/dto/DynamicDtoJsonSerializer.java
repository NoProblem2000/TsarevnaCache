package io.github.noproblem2000.tsarevnacache.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Renders a {@link DynamicDtoRecord} as JSON that {@link DynamicDtoConverter} can turn back into
 * the identical record:
 * <ul>
 *   <li>fields in schema order; nested DTOs as objects carrying {@code "@classId"} (and
 *       {@code "@factoryId"} when it is not the project's factory); the top-level record has no
 *       markers because its class is known from the entry</li>
 *   <li>longs outside the exact range of JavaScript numbers as strings</li>
 *   <li>BigDecimals as strings in {@code BigDecimal.toString()} form (precision and scale kept, so
 *   the round trip is byte-identical), dates as ISO-8601</li>
 *   <li>nested values that are neither DTOs, strings nor booleans as
 *       {@code {"@javaType": "...", "value": ...}} so their Java type survives a round trip</li>
 * </ul>
 */
public final class DynamicDtoJsonSerializer extends StdSerializer<DynamicDtoRecord> {

    public DynamicDtoJsonSerializer() {
        super(DynamicDtoRecord.class);
    }

    @Override
    public void serialize(DynamicDtoRecord record, JsonGenerator gen, SerializerProvider provider) throws IOException {
        boolean root = gen.getOutputContext().inRoot();
        DtoClassSchema schema = record.schema();
        gen.writeStartObject();
        if (!root) {
            gen.writeNumberField(DynamicDtoConverter.CLASS_ID, record.getClassId());
            if (record.registry() == null || record.getFactoryId() != record.registry().factoryId()) {
                gen.writeNumberField(DynamicDtoConverter.FACTORY_ID, record.getFactoryId());
            }
        }
        for (DtoFieldSchema field : schema.fields()) {
            Object value = record.data().get(field.fieldName());
            gen.writeFieldName(field.fieldName());
            writeField(field.fieldType(), value, gen, provider);
        }
        gen.writeEndObject();
    }

    private static void writeField(DtoFieldType type, Object value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        switch (type) {
            case NULLABLE_LONG, RAW_LONG -> writeLong((Long) value, gen);
            case NULLABLE_BIG_DECIMAL -> gen.writeString(((BigDecimal) value).toString());
            case NULLABLE_LONG_LIST -> {
                gen.writeStartArray();
                for (Object element : (List<?>) value) {
                    if (element == null) {
                        gen.writeNull();
                    } else {
                        writeLong((Long) element, gen);
                    }
                }
                gen.writeEndArray();
            }
            case NULLABLE_OBJECT -> writeNested(value, gen, provider);
            case NULLABLE_LIST -> {
                gen.writeStartArray();
                for (Object element : (List<?>) value) {
                    writeNested(element, gen, provider);
                }
                gen.writeEndArray();
            }
            default -> provider.defaultSerializeValue(value, gen);
        }
    }

    private static void writeNested(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else if (value instanceof DynamicDtoRecord || value instanceof String || value instanceof Boolean) {
            provider.defaultSerializeValue(value, gen);
        } else if (value instanceof List<?> list) {
            gen.writeStartArray();
            for (Object element : list) {
                writeNested(element, gen, provider);
            }
            gen.writeEndArray();
        } else {
            gen.writeStartObject();
            gen.writeStringField(DynamicDtoConverter.JAVA_TYPE, value.getClass().getName());
            gen.writeFieldName(DynamicDtoConverter.VALUE);
            if (value instanceof Long number) {
                writeLong(number, gen);
            } else if (value instanceof BigDecimal decimal) {
                gen.writeString(decimal.toString());
            } else {
                provider.defaultSerializeValue(value, gen);
            }
            gen.writeEndObject();
        }
    }

    private static void writeLong(Long value, JsonGenerator gen) throws IOException {
        if (DynamicDtoConverter.isSafeJsonInteger(value)) {
            gen.writeNumber(value);
        } else {
            gen.writeString(Long.toString(value));
        }
    }
}
