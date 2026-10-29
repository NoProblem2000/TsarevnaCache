package io.github.noproblem2000.tsarevnacache.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.config.properties.DtoSchemaProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DtoSchemaRegistryServiceTest {

    @TempDir
    Path dir;

    private DtoSchemaRegistryService open() {
        DtoSchemaProperties properties = new DtoSchemaProperties();
        properties.setFile(dir.resolve("nested/dto-schemas.json").toString());
        return new DtoSchemaRegistryService(properties, new ObjectMapper());
    }

    private static DtoClassSchema schema(int classId, String name, DtoFieldSchema... fields) {
        return new DtoClassSchema(null, classId, name, List.of(fields), null);
    }

    private static DtoFieldSchema field(int order, String name, DtoFieldType type, String nestedClass) {
        return new DtoFieldSchema(order, name, type, null, nestedClass);
    }

    @Test
    void schemasArePersistedAndReloaded() throws Exception {
        DtoSchemaRegistryService registry = open();
        assertThat(registry.list()).isEmpty();
        registry.updateFactory(1, Map.of("CHILD", 2), Map.of("Child", 2));
        registry.save(schema(1, "Parent",
                field(1, "child", DtoFieldType.NULLABLE_OBJECT, "Child"),
                field(0, "id", DtoFieldType.RAW_LONG, null)));

        assertThat(Files.readString(registry.file())).contains("\"Parent\"");
        DtoSchemaRegistryService reloaded = open();
        DtoClassSchema parent = reloaded.find(1, 1).orElseThrow();
        assertThat(parent.fields()).extracting(DtoFieldSchema::fieldName).containsExactly("id", "child");
        assertThat(parent.fields().get(1).nestedClassId()).isEqualTo(2);
        assertThat(reloaded.resolveClassId("Child")).contains(2);
        assertThat(reloaded.resolveConstant("CHILD")).contains(2);
        assertThat(reloaded.factoryIds()).containsExactly(1);
    }

    @Test
    void schemasAreKeyedStrictlyByFactoryAndClassId() {
        DtoSchemaRegistryService registry = open();
        registry.save(schema(1, "Order"));
        DtoSchemaRegistryService.SaveOutcome again = registry.saveDetailed(schema(5, "Order"));
        assertThat(registry.list()).extracting(DtoClassSchema::classId).containsExactly(1, 5);
        assertThat(again.warnings()).singleElement().asString().contains("also registered with classId 1");
        assertThat(registry.delete(1, 5)).isTrue();
        assertThat(registry.delete(1, 5)).isFalse();
        assertThat(registry.find(1, 1)).isPresent();
        assertThat(new DtoClassSchema.SchemaKey(1, 5)).hasToString("1:5");
    }

    @Test
    void manySchemasAddedOneByOneAreAllKeptAndDeletingThemKeepsTheFactory() {
        DtoSchemaRegistryService registry = open();
        Map<String, Integer> constants = new java.util.LinkedHashMap<>();
        for (int id = 1; id <= 12; id++) {
            constants.put("DTO" + id, id);
        }
        registry.updateFactory(1, constants, Map.of());
        for (int id = 12; id >= 1; id--) {
            registry.save(schema(id, "Dto" + id, field(0, "value", DtoFieldType.NULLABLE_STRING, null)));
        }
        assertThat(registry.list()).hasSize(12);
        assertThat(open().list()).hasSize(12);
        for (int id = 1; id <= 12; id++) {
            assertThat(registry.delete(1, id)).isTrue();
        }
        DtoSchemaRegistryService reloaded = open();
        assertThat(reloaded.list()).isEmpty();
        assertThat(reloaded.factoryConstants()).hasSize(12).containsEntry("DTO7", 7);
    }

    @Test
    void nestedClassIdIsLinkedWhateverTheOrderOfAdding() {
        DtoSchemaRegistryService registry = open();
        registry.save(schema(1, "Parent", field(0, "child", DtoFieldType.NULLABLE_OBJECT, "Child"),
                field(1, "children", DtoFieldType.NULLABLE_LIST, "Child")));
        assertThat(registry.find(1, 1).orElseThrow().fields()).extracting(DtoFieldSchema::nestedClassId)
                .containsOnlyNulls();

        DtoSchemaRegistryService.SaveOutcome child = registry.saveDetailed(schema(7, "Child"));
        assertThat(child.linked()).containsExactly(new DtoClassSchema.SchemaKey(1, 1));
        assertThat(registry.find(1, 1).orElseThrow().fields()).extracting(DtoFieldSchema::nestedClassId)
                .containsExactly(7, 7);
        assertThat(open().find(1, 1).orElseThrow().fields().get(0).nestedClassId()).isEqualTo(7);
    }

    @Test
    void namesAreMatchedWithoutCaseAndUnderscores() {
        DtoSchemaRegistryService registry = open();
        registry.updateFactory(1, Map.of("SOMEDTO", 1, "SOME_OTHER_DTO", 3, "SOMEDTO2", 2), Map.of("Some2DTO", 2));
        assertThat(registry.resolveConstant("SomeDTO")).contains(1);
        assertThat(registry.resolveConstant("SOMEDTO2")).contains(2);
        assertThat(registry.resolveClassId("SomeDTO")).contains(1);
        assertThat(registry.resolveClassId("SomeOtherDto")).contains(3);
        assertThat(registry.resolveClassId("Some2DTO")).contains(2);
        assertThat(registry.resolveClassId("Unknown")).isEmpty();
        assertThat(DtoSchemaRegistryService.normalize("Some_DTO")).isEqualTo("somedto");
    }

    @Test
    void importingSchemasOnlyKeepsTheFactoryMapping() {
        DtoSchemaRegistryService registry = open();
        registry.updateFactory(4, Map.of("ORDER", 2), Map.of("Order", 2));
        registry.save(schema(9, "Old"));
        registry.importFile(new DtoSchemaRegistryFile(1, 4, Map.of(), Map.of(), List.of(schema(2, "Order"))), true);
        assertThat(registry.list()).extracting(DtoClassSchema::className).containsExactly("Order");
        assertThat(registry.factoryId()).isEqualTo(4);
        assertThat(registry.factoryConstants()).containsEntry("ORDER", 2);

        registry.importFile(new DtoSchemaRegistryFile(1, 6, Map.of("X", 1), Map.of(), List.of()), true);
        assertThat(registry.factoryId()).isEqualTo(6);
        assertThat(registry.factoryConstants()).containsOnlyKeys("X");
    }

    @Test
    void invalidSchemasAreRejected() {
        DtoSchemaRegistryService registry = open();
        assertInvalid(() -> registry.save(schema(1, "Bad name")));
        assertInvalid(() -> registry.save(schema(1, "Dup",
                field(0, "a", DtoFieldType.RAW_INT, null), field(1, "a", DtoFieldType.RAW_INT, null))));
        assertInvalid(() -> registry.save(schema(1, "SameOrder",
                field(0, "a", DtoFieldType.RAW_INT, null), field(0, "b", DtoFieldType.RAW_INT, null))));
        assertInvalid(() -> registry.save(schema(1, "Marker", field(0, "@classId", DtoFieldType.RAW_INT, null))));
        assertInvalid(() -> registry.save(schema(1, "NoType", field(0, "a", null, null))));
        assertThat(registry.list()).isEmpty();
    }

    @Test
    void brokenFileFailsFastInsteadOfBeingOverwritten() throws Exception {
        Path file = dir.resolve("nested/dto-schemas.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ not json");
        assertThatThrownBy(this::open).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot read the DTO schema registry");
        assertThat(Files.readString(file)).isEqualTo("{ not json");
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DTO_SCHEMA_INVALID));
    }
}
