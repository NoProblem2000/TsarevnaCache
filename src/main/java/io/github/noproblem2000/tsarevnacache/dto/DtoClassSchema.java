package io.github.noproblem2000.tsarevnacache.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.List;

/**
 * Binary layout of one {@code IdentifiedDataSerializable} DTO class.
 *
 * @param factoryId     factory id ({@code getFactoryId()}), {@code 1} when omitted
 * @param classId       class id ({@code getClassId()}); may be {@code null} only in parser previews
 * @param className     simple class name, e.g. {@code SomeDTO}
 * @param fields        fields in binary order
 * @param rawJavaSource Java source the schema was parsed from (optional, kept for reference)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DtoClassSchema(
        Integer factoryId,
        @NotNull Integer classId,
        @NotBlank String className,
        @NotNull @Valid List<DtoFieldSchema> fields,
        String rawJavaSource) {

    public static final int DEFAULT_FACTORY_ID = 1;

    public DtoClassSchema {
        factoryId = factoryId == null ? DEFAULT_FACTORY_ID : factoryId;
        fields = fields == null ? List.of()
                : fields.stream().sorted(Comparator.comparingInt(DtoFieldSchema::order)).toList();
    }

    /** Registry key of this schema. */
    public SchemaKey key() {
        return new SchemaKey(factoryId, classId);
    }

    /** Identity of a schema: factory id + class id. */
    public record SchemaKey(int factoryId, int classId) implements Comparable<SchemaKey> {

        @Override
        public int compareTo(SchemaKey other) {
            int byFactory = Integer.compare(factoryId, other.factoryId);
            return byFactory != 0 ? byFactory : Integer.compare(classId, other.classId);
        }

        @Override
        public String toString() {
            return factoryId + "/" + classId;
        }
    }
}
