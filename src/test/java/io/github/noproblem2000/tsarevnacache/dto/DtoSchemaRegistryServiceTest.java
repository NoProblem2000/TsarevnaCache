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
    void renumberedClassReplacesItsOldId() {
        DtoSchemaRegistryService registry = open();
        registry.save(schema(1, "Order"));
        registry.save(schema(5, "Order"));
        assertThat(registry.list()).extracting(DtoClassSchema::classId).containsExactly(5);
        assertThat(registry.delete(1, 5)).isTrue();
        assertThat(registry.delete(1, 5)).isFalse();
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
