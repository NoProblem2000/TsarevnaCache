package io.github.noproblem2000.tsarevnacache.inspector;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import java.io.IOException;

/**
 * Builds the dedicated {@link ObjectMapper} used to convert cluster objects to JSON and back.
 *
 * <p>Unlike the API mapper it binds <b>fields</b> of any visibility and ignores getters/setters, so
 * DTOs without accessors, without {@code Serializable} and with private fields round-trip as they
 * are stored. {@link GenericRecord} and {@link HazelcastJsonValue} get dedicated serializers, which
 * also covers them when nested inside collections or POJOs.
 */
final class InspectorObjectMapperFactory {

    private InspectorObjectMapperFactory() {
    }

    static ObjectMapper create() {
        SimpleModule hazelcastModule = new SimpleModule("tsarevna-hazelcast");
        hazelcastModule.addSerializer(GenericRecord.class, new GenericRecordSerializer());
        hazelcastModule.addSerializer(HazelcastJsonValue.class, new HazelcastJsonValueSerializer());

        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(new Jdk8Module())
                .addModule(hazelcastModule)
                .visibility(PropertyAccessor.ALL, Visibility.NONE)
                .visibility(PropertyAccessor.FIELD, Visibility.ANY)
                .visibility(PropertyAccessor.CREATOR, Visibility.ANY)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
                .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                .build();
    }

    /** Writes a record as a JSON object of its fields. */
    private static final class GenericRecordSerializer extends StdSerializer<GenericRecord> {

        GenericRecordSerializer() {
            super(GenericRecord.class);
        }

        @Override
        public void serialize(GenericRecord record, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            for (String name : record.getFieldNames()) {
                gen.writeFieldName(name);
                provider.defaultSerializeValue(
                        GenericRecordSupport.readField(record, name, record.getFieldKind(name)), gen);
            }
            gen.writeEndObject();
        }
    }

    /** Embeds the JSON document itself (or its text when it is not valid JSON). */
    private static final class HazelcastJsonValueSerializer extends StdSerializer<HazelcastJsonValue> {

        private static final ObjectMapper READER = JsonMapper.builder()
                .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                .build();

        HazelcastJsonValueSerializer() {
            super(HazelcastJsonValue.class);
        }

        @Override
        public void serialize(HazelcastJsonValue value, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            try {
                provider.defaultSerializeValue(READER.readTree(value.getValue()), gen);
            } catch (JsonProcessingException e) {
                gen.writeString(value.getValue());
            }
        }
    }
}
