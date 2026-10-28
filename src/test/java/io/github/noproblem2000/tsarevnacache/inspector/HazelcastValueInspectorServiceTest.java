package io.github.noproblem2000.tsarevnacache.inspector;

import static io.github.noproblem2000.tsarevnacache.support.EmbeddedCluster.field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.nio.serialization.FieldKind;
import io.github.noproblem2000.tsarevnacache.api.dto.DataType;
import io.github.noproblem2000.tsarevnacache.api.dto.DeserializationErrorCode;
import io.github.noproblem2000.tsarevnacache.api.dto.InspectedValue;
import io.github.noproblem2000.tsarevnacache.api.dto.RecordFieldDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.RecordSchemaDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.RecordSchemaDTO.RecordFormat;
import io.github.noproblem2000.tsarevnacache.api.dto.TypedValueInput;
import io.github.noproblem2000.tsarevnacache.api.dto.ValueKind;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.support.AbstractIntegrationTest;
import io.github.noproblem2000.tsarevnacache.support.HiddenClasses;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import testdto.NoDefaultConstructorDto;
import testdto.PlainDto;
import testdto.RecordDto;

class HazelcastValueInspectorServiceTest extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private HazelcastValueInspectorService inspector;

    @Autowired
    private HazelcastInstance hazelcastClient;

    /** Stores the object through the member (which knows the hidden classes) and returns its binary. */
    private Data storedByMember(String mapName, Object value) {
        cluster().member().getMap(mapName).put("key", value);
        return cluster().memberSerialization().toData(value);
    }

    @Nested
    class Scalars {

        @Test
        void stringIsReturnedAsText() {
            InspectedValue value = inspector.inspect(inspector.toData("hello"));
            assertThat(value.deserialized()).isTrue();
            assertThat(value.kind()).isEqualTo(ValueKind.STRING);
            assertThat(value.dataType()).isEqualTo(DataType.STRING);
            assertThat(value.value().textValue()).isEqualTo("hello");
            assertThat(value.preview()).isEqualTo("hello");
            assertThat(value.serializedSize()).isPositive();
        }

        @Test
        void numbersBooleansAndUuidKeepTheirType() {
            assertThat(inspector.inspect(inspector.toData(42L)).dataType()).isEqualTo(DataType.LONG);
            assertThat(inspector.inspect(inspector.toData(42)).dataType()).isEqualTo(DataType.INTEGER);
            assertThat(inspector.inspect(inspector.toData(1.5d)).dataType()).isEqualTo(DataType.DOUBLE);
            assertThat(inspector.inspect(inspector.toData(true)).kind()).isEqualTo(ValueKind.BOOLEAN);

            InspectedValue decimal = inspector.inspect(inspector.toData(new BigDecimal("1000.50")));
            assertThat(decimal.preview()).isEqualTo("1000.50");

            UUID uuid = UUID.randomUUID();
            InspectedValue uuidValue = inspector.inspect(inspector.toData(uuid));
            assertThat(uuidValue.kind()).isEqualTo(ValueKind.UUID);
            assertThat(uuidValue.preview()).isEqualTo(uuid.toString());
        }

        @Test
        void longStringPreviewIsTruncated() {
            String text = "x".repeat(500);
            InspectedValue value = inspector.inspect(inspector.toData(text));
            assertThat(value.preview()).hasSize(160);
            assertThat(value.truncated()).isTrue();
            assertThat(value.value().textValue()).hasSize(500);
        }

        @Test
        void nullIsSupported() {
            assertThat(inspector.inspect((Object) null).kind()).isEqualTo(ValueKind.NULL);
        }
    }

    @Nested
    class JsonValues {

        @Test
        void jsonTextIsStoredAsHazelcastJsonValueAndRenderedAsObject() {
            Object stored = inspector.toObject(TypedValueInput.ofText("{ \"a\": 1, \"b\": [true] }", DataType.JSON, null));
            assertThat(stored).isEqualTo(new HazelcastJsonValue("{\"a\":1,\"b\":[true]}"));

            InspectedValue value = inspector.inspect(inspector.toData(stored));
            assertThat(value.kind()).isEqualTo(ValueKind.JSON);
            assertThat(value.dataType()).isEqualTo(DataType.JSON);
            assertThat(value.value().get("a").intValue()).isEqualTo(1);
        }

        @Test
        void invalidJsonIsRejected() {
            assertThatThrownBy(() -> inspector.toObject(TypedValueInput.ofText("{bad", DataType.JSON, null)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_JSON));
        }
    }

    @Nested
    class ClasspathDtos {

        @Test
        void dtoWithoutGettersIsRenderedFieldByFieldAndWrittenBack() throws Exception {
            PlainDto dto = new PlainDto("Ann", 31, new String[] {"a", "b"}, LocalDate.of(1990, 5, 17));
            hazelcastClient.getMap("plain").put("ann", dto);

            InspectedValue value = inspector.inspect(inspector.toData(hazelcastClient.getMap("plain").get("ann")));
            assertThat(value.kind()).isEqualTo(ValueKind.OBJECT);
            assertThat(value.javaType()).isEqualTo(PlainDto.class.getName());
            assertThat(value.dataType()).isEqualTo(DataType.CLASS);
            assertThat(value.value().get("name").textValue()).isEqualTo("Ann");
            assertThat(value.value().get("born").textValue()).isEqualTo("1990-05-17");

            Object rebuilt = inspector.toObject(new TypedValueInput(value.value(), DataType.CLASS,
                    PlainDto.class.getName(), null));
            assertThat(rebuilt).isEqualTo(dto);
        }

        @Test
        void recordsRoundTrip() {
            RecordDto dto = new RecordDto("r-1", 12.5, true);
            InspectedValue value = inspector.inspect(inspector.toData(dto));
            assertThat(value.value().get("amount").doubleValue()).isEqualTo(12.5);

            Object rebuilt = inspector.toObject(new TypedValueInput(value.value(), DataType.CLASS,
                    RecordDto.class.getName(), null));
            assertThat(rebuilt).isEqualTo(dto);
        }

        @Test
        void classWithoutNoArgConstructorIsBoundThroughFields() throws Exception {
            Object rebuilt = inspector.toObject(new TypedValueInput(JSON.readTree("{\"code\":\"X\",\"count\":7}"),
                    DataType.CLASS, NoDefaultConstructorDto.class.getName(), null));
            assertThat(rebuilt).isInstanceOfSatisfying(NoDefaultConstructorDto.class, dto -> {
                assertThat(dto.code()).isEqualTo("X");
                assertThat(dto.count()).isEqualTo(7);
            });
        }

        @Test
        void unknownOrDeniedClassesAreRejected() {
            assertThatThrownBy(() -> inspector.toObject(TypedValueInput.ofText("{}", DataType.CLASS, "com.example.Missing")))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.CLASS_NOT_FOUND));
            assertThatThrownBy(() -> inspector.toObject(TypedValueInput.ofText("{}", DataType.CLASS, "java.lang.Runtime")))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.CLASS_NOT_ALLOWED));
        }

        @Test
        void unknownJsonFieldsAreRejected() {
            assertThatThrownBy(() -> inspector.toObject(TypedValueInput.ofText("{\"nope\":1}", DataType.CLASS,
                    RecordDto.class.getName())))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.CLASS_INSTANTIATION_FAILED));
        }
    }

    @Nested
    class CompactWithoutClass {

        private Object hiddenCompact() {
            Object address = cluster().newHidden("HiddenAddress", new Class<?>[] {String.class, int.class}, "Oslo", 150);
            return cluster().newHidden("HiddenCompact",
                    new Class<?>[] {String.class, int.class, Integer.class, address.getClass(), long[].class},
                    "Bob", 40, null, address, new long[] {1, 2});
        }

        @Test
        void isRenderedAsGenericRecordWithSchema() {
            InspectedValue value = inspector.inspect(storedByMember("compact-read", hiddenCompact()));

            assertThat(value.deserialized()).isTrue();
            assertThat(value.kind()).isEqualTo(ValueKind.COMPACT);
            assertThat(value.javaType()).isEqualTo("hidden.HiddenCompact");
            assertThat(value.dataType()).isEqualTo(DataType.COMPACT);
            assertThat(value.value().get("name").textValue()).isEqualTo("Bob");
            assertThat(value.value().get("score").isNull()).isTrue();
            assertThat(value.value().get("address").get("city").textValue()).isEqualTo("Oslo");
            assertThat(value.value().get("tags").get(1).longValue()).isEqualTo(2);

            Map<String, FieldKind> kinds = value.schema().fields().stream()
                    .collect(Collectors.toMap(RecordFieldDTO::name, RecordFieldDTO::kind));
            assertThat(kinds).containsEntry("name", FieldKind.STRING)
                    .containsEntry("age", FieldKind.INT32)
                    .containsEntry("score", FieldKind.NULLABLE_INT32)
                    .containsEntry("address", FieldKind.COMPACT)
                    .containsEntry("tags", FieldKind.ARRAY_OF_INT64);
        }

        @Test
        void editedRecordIsReadableByTheOwningApplication() {
            InspectedValue read = inspector.inspect(storedByMember("compact-write", hiddenCompact()));
            ObjectNode edited = (ObjectNode) read.value().deepCopy();
            edited.put("name", "Changed");
            edited.put("age", 42);
            edited.put("score", 7);

            Object record = inspector.toObject(new TypedValueInput(edited, DataType.COMPACT, null, read.schema()));
            hazelcastClient.getMap("compact-write").put("edited", record);

            Object fromMember = cluster().member().getMap("compact-write").get("edited");
            assertThat(fromMember.getClass().getName()).isEqualTo("hidden.HiddenCompact");
            assertThat(field(fromMember, "name")).isEqualTo("Changed");
            assertThat(field(fromMember, "age")).isEqualTo(42);
            assertThat(field(fromMember, "score")).isEqualTo(7);
            assertThat(field(field(fromMember, "address"), "city")).isEqualTo("Oslo");
        }

        @Test
        void schemaViolationsAreRejected() {
            RecordSchemaDTO schema = new RecordSchemaDTO(RecordFormat.COMPACT, "demo.Type", null, null, null,
                    List.of(new RecordFieldDTO("count", FieldKind.INT32, null)));

            assertThatThrownBy(() -> inspector.toObject(TypedValueInput.ofText("{\"count\":1}", DataType.COMPACT, null)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.SCHEMA_REQUIRED));
            assertThatThrownBy(() -> inspector.toObject(new TypedValueInput(
                    TextNode.valueOf("{\"count\":1,\"extra\":2}"), DataType.COMPACT, null, schema)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.SCHEMA_INVALID));
            assertThatThrownBy(() -> inspector.toObject(new TypedValueInput(
                    TextNode.valueOf("{}"), DataType.COMPACT, null, schema)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.SCHEMA_INVALID));
            assertThatThrownBy(() -> inspector.toObject(new TypedValueInput(
                    TextNode.valueOf("{\"count\":99999999999}"), DataType.COMPACT, null, schema)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALUE_CONVERSION_FAILED));
        }
    }

    @Nested
    class UnresolvedObjects {

        @Test
        void javaSerializableWithMissingClass() {
            Object hidden = cluster().newHidden("HiddenSerializable", new Class<?>[] {String.class, int.class}, "a", 1);
            InspectedValue value = inspector.inspect(storedByMember("unresolved-ser", hidden));

            assertThat(value.deserialized()).isFalse();
            assertThat(value.kind()).isEqualTo(ValueKind.UNRESOLVED);
            assertThat(value.unresolved().deserialized()).isFalse();
            assertThat(value.unresolved().errorCode()).isEqualTo(DeserializationErrorCode.CLASS_NOT_FOUND);
            assertThat(value.unresolved().missingClass()).isEqualTo("hidden.HiddenSerializable");
            assertThat(value.unresolved().serializer()).isEqualTo("Java Serializable");
            assertThat(value.unresolved().serializedSize()).isPositive();
            assertThat(value.unresolved().metadata()).containsEntry("className", "hidden.HiddenSerializable");
        }

        @Test
        void externalizableWithMissingClass() {
            Object hidden = cluster().newHidden("HiddenExternalizable", new Class<?>[] {String.class}, "a");
            InspectedValue value = inspector.inspect(storedByMember("unresolved-ext", hidden));

            assertThat(value.unresolved().errorCode()).isEqualTo(DeserializationErrorCode.CLASS_NOT_FOUND);
            assertThat(value.unresolved().missingClass()).isEqualTo("hidden.HiddenExternalizable");
        }

        @Test
        void dataSerializableWithMissingClass() {
            Object hidden = cluster().newHidden("HiddenDataSerializable", new Class<?>[] {String.class}, "a");
            InspectedValue value = inspector.inspect(storedByMember("unresolved-ds", hidden));

            assertThat(value.unresolved().errorCode()).isEqualTo(DeserializationErrorCode.CLASS_NOT_FOUND);
            assertThat(value.unresolved().missingClass()).isEqualTo("hidden.HiddenDataSerializable");
            assertThat(value.unresolved().metadata()).containsEntry("identified", false);
        }

        @Test
        void identifiedDataSerializableWithMissingFactory() {
            Object hidden = cluster().newHidden("HiddenIdentified", new Class<?>[] {String.class}, "a");
            InspectedValue value = inspector.inspect(storedByMember("unresolved-ids", hidden));

            assertThat(value.unresolved().errorCode())
                    .isEqualTo(DeserializationErrorCode.DATA_SERIALIZABLE_FACTORY_MISSING);
            assertThat(value.unresolved().metadata())
                    .containsEntry("factoryId", HiddenClasses.IDS_FACTORY_ID)
                    .containsEntry("classId", 1);
        }

        @Test
        void portableWithoutFactoryIsReadAsGenericRecord() {
            Object hidden = cluster().newHidden("HiddenPortable", new Class<?>[] {String.class}, "a");
            InspectedValue value = inspector.inspect(storedByMember("unresolved-portable", hidden));

            assertThat(value.deserialized()).isTrue();
            assertThat(value.kind()).isEqualTo(ValueKind.PORTABLE);
            assertThat(value.dataType()).isNull();
            assertThat(value.value().get("name").textValue()).isEqualTo("a");
            assertThat(value.schema().format()).isEqualTo(RecordFormat.PORTABLE);
            assertThat(value.schema().factoryId()).isEqualTo(HiddenClasses.PORTABLE_FACTORY_ID);
            assertThat(value.schema().classId()).isEqualTo(1);
        }

        @Test
        void customSerializerMissing() {
            Object hidden = cluster().newHidden("HiddenCustom", new Class<?>[] {String.class}, "a");
            InspectedValue value = inspector.inspect(storedByMember("unresolved-custom", hidden));

            assertThat(value.unresolved().errorCode()).isEqualTo(DeserializationErrorCode.CUSTOM_SERIALIZER_MISSING);
            assertThat(value.unresolved().typeId()).isEqualTo(HiddenClasses.CUSTOM_TYPE_ID);
        }

        @Test
        void entryCostCanBeAttached() {
            Object hidden = cluster().newHidden("HiddenSerializable", new Class<?>[] {String.class, int.class}, "a", 1);
            InspectedValue value = inspector.inspect(storedByMember("unresolved-cost", hidden)).withEntryCost(123L);
            assertThat(value.unresolved().entryCost()).isEqualTo(123L);
        }
    }

    @Nested
    class TypedInputs {

        @Test
        void scalarsAreParsedFromTextAndJson() throws Exception {
            assertThat(inspector.toObject(TypedValueInput.ofText(" 42 ", DataType.LONG, null))).isEqualTo(42L);
            assertThat(inspector.toObject(new TypedValueInput(JSON.readTree("7"), DataType.INTEGER, null, null)))
                    .isEqualTo(7);
            assertThat(inspector.toObject(TypedValueInput.ofText("TRUE", DataType.BOOLEAN, null))).isEqualTo(true);
            UUID uuid = UUID.randomUUID();
            assertThat(inspector.toObject(TypedValueInput.ofText(uuid.toString(), DataType.UUID, null))).isEqualTo(uuid);
            assertThat(inspector.toObject(new TypedValueInput(JSON.readTree("{\"a\":1}"), DataType.STRING, null, null)))
                    .isEqualTo("{\"a\":1}");
        }

        @Test
        void invalidScalarsAreRejected() {
            assertThatThrownBy(() -> inspector.toObject(TypedValueInput.ofText("abc", DataType.INTEGER, null)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALUE_CONVERSION_FAILED));
            assertThatThrownBy(() -> inspector.toObject(TypedValueInput.ofText("3000000000", DataType.INTEGER, null)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALUE_CONVERSION_FAILED));
            assertThatThrownBy(() -> inspector.toObject(TypedValueInput.ofText("not-a-uuid", DataType.UUID, null)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALUE_CONVERSION_FAILED));
            assertThatThrownBy(() -> inspector.toObject(TypedValueInput.ofText(null, DataType.STRING, null)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALUE_REQUIRED));
        }

        @Test
        void keyReferencesRoundTrip() {
            Data key = inspector.toData(TypedValueInput.ofText("order-1", DataType.STRING, null));
            Data decoded = DataRefs.decode(DataRefs.encode(key));
            assertThat(decoded).isEqualTo(key);
            assertThat(inspector.inspect(decoded).preview()).isEqualTo("order-1");

            assertThatThrownBy(() -> DataRefs.decode("%%%"))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_KEY_REF));
        }
    }
}
