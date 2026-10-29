package io.github.noproblem2000.tsarevnacache.api;

import static io.github.noproblem2000.tsarevnacache.support.EmbeddedCluster.field;
import static io.github.noproblem2000.tsarevnacache.support.EmbeddedCluster.setField;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.noproblem2000.tsarevnacache.api.dto.DataType;
import io.github.noproblem2000.tsarevnacache.api.dto.TypedValueInput;
import io.github.noproblem2000.tsarevnacache.dto.DtoSchemaRegistryFile;
import io.github.noproblem2000.tsarevnacache.inspector.HazelcastValueInspectorService;
import io.github.noproblem2000.tsarevnacache.support.AbstractApiTest;
import io.github.noproblem2000.tsarevnacache.support.ProductionDtoSources;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * End to end: the member owns the real {@code prod.model} classes, the admin service only knows the
 * schemas parsed from their pasted sources and still reads, edits and writes byte-identical values.
 */
class DynamicDtoApiTest extends AbstractApiTest {

    private static final long UNSAFE_LONG = 9_007_199_254_740_993L;

    @Autowired
    private HazelcastValueInspectorService inspector;

    @BeforeEach
    void registerSchemasFromSources() throws Exception {
        assertThat(httpPostJson("/api/schemas/import?mode=replace", DtoSchemaRegistryFile.empty()).status())
                .isEqualTo(200);
        assertThat(httpPostJson("/api/schemas/parse-factory", source(ProductionDtoSources.FACTORY)).status())
                .isEqualTo(200);
        for (String dto : List.of(ProductionDtoSources.SOME2_DTO, ProductionDtoSources.SOME_DTO)) {
            Response parsed = httpPostJson("/api/schemas/parse-dto", source(dto));
            assertThat(parsed.status()).isEqualTo(200);
            Response saved = httpPostJson("/api/schemas", parsed.body().path("schema"));
            assertThat(saved.status()).isEqualTo(200);
            assertThat(saved.body().path("restartRequired").asBoolean()).isFalse();
        }
    }

    @Test
    void factoryAndDtoSourcesAreParsed() throws Exception {
        JsonNode factory = httpPostJson("/api/schemas/parse-factory?save=false", source(ProductionDtoSources.FACTORY))
                .body();
        assertThat(factory.path("factoryId").asInt()).isEqualTo(1);
        assertThat(factory.path("constants").path("SOMEDTO").asInt()).isEqualTo(1);
        assertThat(factory.path("constants").path("UNKNOWNDTO").asInt()).isEqualTo(3);
        assertThat(factory.path("classes").path("Some2DTO").asInt()).isEqualTo(2);

        JsonNode parsed = httpPostJson("/api/schemas/parse-dto", source(ProductionDtoSources.SOME_DTO)).body();
        assertThat(parsed.path("complete").asBoolean()).isTrue();
        assertThat(parsed.path("source").asText()).isEqualTo("readData");
        assertThat(parsed.path("warnings").size()).isZero();
        JsonNode schema = parsed.path("schema");
        assertThat(schema.path("factoryId").asInt()).isEqualTo(1);
        assertThat(schema.path("classId").asInt()).isEqualTo(1);
        assertThat(schema.path("className").asText()).isEqualTo("SomeDTO");
        assertThat(schema.path("rawJavaSource").asText()).contains("class SomeDTO");

        List<String> fields = schema.path("fields").findValuesAsText("fieldName");
        assertThat(fields).containsExactly("id", "name", "createdAt", "details", "active", "count", "ratio",
                "amount", "items", "refs", "scores", "version", "stamp", "flag", "code");
        List<String> types = schema.path("fields").findValuesAsText("fieldType");
        assertThat(types).containsExactly("NULLABLE_LONG", "NULLABLE_STRING", "NULLABLE_DATE", "NULLABLE_OBJECT",
                "NULLABLE_BOOLEAN", "NULLABLE_INT", "NULLABLE_DOUBLE", "NULLABLE_BIG_DECIMAL", "NULLABLE_LIST",
                "NULLABLE_LONG_LIST", "NULLABLE_INTEGER_LIST", "RAW_INT", "RAW_LONG", "RAW_BOOLEAN", "RAW_STRING");
        JsonNode details = schema.path("fields").get(3);
        assertThat(details.path("nestedClassName").asText()).isEqualTo("Some2DTO");
        assertThat(details.path("nestedClassId").asInt()).isEqualTo(2);
        assertThat(schema.path("fields").get(8).path("nestedClassName").asText()).isEqualTo("Some2DTO");

        JsonNode registry = httpGet("/api/schemas").body();
        assertThat(registry.path("schemas").size()).isEqualTo(2);
        assertThat(registry.path("activeFactoryIds").toString()).isEqualTo("[1]");
        assertThat(registry.path("pendingFactoryIds").size()).isZero();
        assertThat(Files.readString(DTO_SCHEMAS_FILE)).contains("\"SomeDTO\"").contains("NULLABLE_INTEGER_LIST");
    }

    @Test
    void memberWrittenDtoIsReadAndRewrittenByteForByte() throws Exception {
        Object dto = someDto();
        cluster().member().getMap("dto-map").put("order-1", dto);

        JsonNode value = httpGet("/api/maps/dto-map/entry?key=order-1&keyType=STRING").body().path("value");
        assertThat(value.path("deserialized").asBoolean()).isTrue();
        assertThat(value.path("kind").asText()).isEqualTo("IDENTIFIED_DTO");
        assertThat(value.path("dataType").asText()).isEqualTo("IDENTIFIED_DTO");
        assertThat(value.path("javaType").asText()).isEqualTo("SomeDTO");
        assertThat(value.path("metadata").path("classId").asInt()).isEqualTo(1);

        JsonNode json = value.path("value");
        assertThat(json.path("id").asText()).isEqualTo(String.valueOf(UNSAFE_LONG));
        assertThat(json.path("name").asText()).isEqualTo("Ann");
        assertThat(json.path("details").path("@classId").asInt()).isEqualTo(2);
        assertThat(json.path("details").path("label").asText()).isEqualTo("inner");
        assertThat(json.path("active").asBoolean()).isTrue();
        assertThat(json.path("count").isNull()).isTrue();
        assertThat(json.path("amount").asText()).isEqualTo("1.25E+5");
        assertThat(json.path("items").size()).isEqualTo(2);
        assertThat(json.path("items").get(1).path("label").isNull()).isTrue();
        assertThat(json.path("refs").toString()).isEqualTo("[1,null,3]");
        assertThat(json.path("scores").toString()).isEqualTo("[7,null]");
        assertThat(json.path("version").asInt()).isEqualTo(3);
        assertThat(json.path("stamp").asLong()).isEqualTo(-1L);
        assertThat(json.path("code").asText()).isEqualTo("X");

        byte[] memberBytes = cluster().memberSerialization().toData(dto).toByteArray();
        byte[] adminBytes = inspector.toData(
                new TypedValueInput(json, DataType.IDENTIFIED_DTO, null, null, null, 1)).toByteArray();
        assertThat(Arrays.equals(adminBytes, memberBytes))
                .as("admin re-serialization must be identical to the owning application's bytes")
                .isTrue();

        ObjectNode edited = (ObjectNode) json.deepCopy();
        edited.put("name", "Bob");
        ((ObjectNode) edited.path("details")).put("label", "changed");
        ((ArrayNode) edited.path("items")).addObject().put("label", "third").put("value", 33);
        ObjectNode put = JSON.createObjectNode();
        put.put("key", "order-1");
        put.put("keyType", "STRING");
        put.set("value", edited);
        put.put("valueType", "IDENTIFIED_DTO");
        put.put("valueClassName", "SomeDTO");
        Response stored = httpPostJson("/api/maps/dto-map/entry", put);
        assertThat(stored.status()).isEqualTo(200);
        assertThat(stored.body().path("value").path("value").path("name").asText()).isEqualTo("Bob");

        Object readByMember = cluster().member().getMap("dto-map").get("order-1");
        assertThat(readByMember.getClass().getName()).isEqualTo("prod.model.SomeDTO");
        assertThat(field(readByMember, "name")).isEqualTo("Bob");
        assertThat(field(readByMember, "id")).isEqualTo(UNSAFE_LONG);
        assertThat(field(readByMember, "createdAt")).isEqualTo(new Date(1_700_000_000_123L));
        assertThat(field(readByMember, "amount")).isEqualTo(new BigDecimal("1.25E+5"));
        assertThat(field(field(readByMember, "details"), "label")).isEqualTo("changed");
        List<?> items = (List<?>) field(readByMember, "items");
        assertThat(items).hasSize(3);
        assertThat(field(items.get(2), "value")).isEqualTo(33L);
        assertThat(field(readByMember, "refs")).isEqualTo(Arrays.asList(1L, null, 3L));
    }

    @Test
    void dtoWithoutSchemaIsReportedAsMissingSchema() throws Exception {
        cluster().member().getMap("dto-unknown").put("u", cluster().newMemberObject("prod.model.UnknownDTO"));

        JsonNode unresolved = httpGet("/api/maps/dto-unknown/entry?key=u&keyType=STRING").body()
                .path("value").path("unresolved");
        assertThat(unresolved.path("errorCode").asText()).isEqualTo("DTO_SCHEMA_MISSING");
        assertThat(unresolved.path("metadata").path("missingFactoryId").asInt()).isEqualTo(1);
        assertThat(unresolved.path("metadata").path("missingClassId").asInt()).isEqualTo(3);
        assertThat(unresolved.path("missingClass").asText()).isEqualTo("UnknownDTO (factoryId=1, classId=3)");
    }

    @Test
    void missingNestedSchemaIsReportedWithItsClassId() throws Exception {
        cluster().member().getMap("dto-nested").put("n", someDto());
        assertThat(httpDelete("/api/schemas/1/2").status()).isEqualTo(204);

        JsonNode unresolved = httpGet("/api/maps/dto-nested/entry?key=n&keyType=STRING").body()
                .path("value").path("unresolved");
        assertThat(unresolved.path("errorCode").asText()).isEqualTo("DTO_SCHEMA_MISSING");
        assertThat(unresolved.path("metadata").path("missingClassId").asInt()).isEqualTo(2);
        assertThat(unresolved.path("missingClass").asText()).isEqualTo("Some2DTO (factoryId=1, classId=2)");

        assertThat(httpDelete("/api/schemas/1/2").code()).isEqualTo("DTO_SCHEMA_NOT_FOUND");
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        cluster().member().getMap("dto-bad").put("seed", "seed");
        ObjectNode put = JSON.createObjectNode();
        put.put("key", "bad");
        put.put("keyType", "STRING");
        put.set("value", JSON.readTree("{\"label\":\"x\",\"unknownField\":1}"));
        put.put("valueType", "IDENTIFIED_DTO");
        put.put("valueClassId", 2);
        assertThat(httpPostJson("/api/maps/dto-bad/entry", put).code()).isEqualTo("DTO_SCHEMA_INVALID");

        put.set("value", JSON.readTree("{\"label\":\"x\"}"));
        put.put("valueClassId", 99);
        assertThat(httpPostJson("/api/maps/dto-bad/entry", put).code()).isEqualTo("DTO_SCHEMA_NOT_FOUND");

        put.remove("valueClassId");
        assertThat(httpPostJson("/api/maps/dto-bad/entry", put).code()).isEqualTo("VALUE_REQUIRED");

        ObjectNode duplicate = (ObjectNode) JSON.readTree("""
                {"classId": 50, "className": "Dup", "fields": [
                  {"order": 0, "fieldName": "a", "fieldType": "NULLABLE_LONG"},
                  {"order": 1, "fieldName": "a", "fieldType": "RAW_INT"}]}""");
        assertThat(httpPostJson("/api/schemas", duplicate).code()).isEqualTo("DTO_SCHEMA_INVALID");

        assertThat(httpPostJson("/api/schemas/parse-dto", source("just some text")).code())
                .isEqualTo("DTO_SOURCE_NOT_RECOGNIZED");
        assertThat(httpPostJson("/api/schemas/parse-factory", source("class Nothing {}")).code())
                .isEqualTo("DTO_SOURCE_NOT_RECOGNIZED");
    }

    @Test
    void schemaOfAnotherFactoryNeedsARestart() throws Exception {
        JsonNode other = JSON.readTree("""
                {"factoryId": 77, "classId": 1, "className": "Other", "fields": [
                  {"order": 0, "fieldName": "id", "fieldType": "RAW_LONG"}]}""");
        Response saved = httpPostJson("/api/schemas", other);
        assertThat(saved.status()).isEqualTo(200);
        assertThat(saved.body().path("restartRequired").asBoolean()).isTrue();
        assertThat(httpGet("/api/schemas").body().path("pendingFactoryIds").toString()).isEqualTo("[77]");
    }

    @Test
    void registryIsExportedAndImported() throws Exception {
        JsonNode exported = httpGet("/api/schemas/export").body();
        assertThat(exported.path("formatVersion").asInt()).isEqualTo(1);
        assertThat(exported.path("schemas").size()).isEqualTo(2);

        httpPostJson("/api/schemas/import?mode=replace", DtoSchemaRegistryFile.empty());
        assertThat(httpGet("/api/schemas").body().path("schemas").size()).isZero();
        assertThat(Files.readString(DTO_SCHEMAS_FILE)).doesNotContain("SomeDTO");

        JsonNode restored = httpPostJson("/api/schemas/import", exported).body();
        assertThat(restored.path("schemas").size()).isEqualTo(2);
        assertThat(restored.path("factoryClasses").path("SomeDTO").asInt()).isEqualTo(1);

        ObjectNode extra = JSON.createObjectNode();
        extra.set("schemas", JSON.readTree("""
                [{"classId": 9, "className": "Extra", "fields": []}]"""));
        assertThat(httpPostJson("/api/schemas/import?mode=merge", extra).body().path("schemas").size()).isEqualTo(3);
        assertThat(httpPostJson("/api/schemas/import?mode=other", extra).code()).isEqualTo("VALIDATION_FAILED");
    }

    private static Map<String, String> source(String javaSource) {
        return Map.of("source", javaSource);
    }

    private Object some2Dto(String label, Long value) {
        Object dto = cluster().newMemberObject("prod.model.Some2DTO");
        setField(dto, "label", label);
        return setField(dto, "value", value);
    }

    private Object someDto() {
        Object dto = cluster().newMemberObject("prod.model.SomeDTO");
        setField(dto, "id", UNSAFE_LONG);
        setField(dto, "name", "Ann");
        setField(dto, "createdAt", new Date(1_700_000_000_123L));
        setField(dto, "details", some2Dto("inner", 5L));
        setField(dto, "active", Boolean.TRUE);
        setField(dto, "count", null);
        setField(dto, "ratio", 0.25);
        setField(dto, "amount", new BigDecimal("1.25E+5"));
        setField(dto, "items", Arrays.asList(some2Dto("a", 1L), some2Dto(null, null)));
        setField(dto, "refs", Arrays.asList(1L, null, 3L));
        setField(dto, "scores", Arrays.asList(7, null));
        setField(dto, "version", 3);
        setField(dto, "stamp", -1L);
        setField(dto, "flag", true);
        return setField(dto, "code", "X");
    }
}
