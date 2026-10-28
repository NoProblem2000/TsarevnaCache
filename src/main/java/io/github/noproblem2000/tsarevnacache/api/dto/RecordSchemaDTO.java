package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Structure of a {@code GenericRecord}. Produced when reading Compact/Portable data and accepted
 * back (Compact only) to write a value without having its Java class.
 *
 * @param format    record format
 * @param typeName  Compact type name (Compact only)
 * @param factoryId Portable factory id (Portable only)
 * @param classId   Portable class id (Portable only)
 * @param version   Portable class version (Portable only)
 * @param fields    fields in schema order
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecordSchemaDTO(
        @NotNull RecordFormat format,
        @NotBlank String typeName,
        Integer factoryId,
        Integer classId,
        Integer version,
        @NotNull @Valid List<RecordFieldDTO> fields) {

    public RecordSchemaDTO {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    /** Serialization format of a {@code GenericRecord}. */
    public enum RecordFormat {
        COMPACT,
        PORTABLE
    }
}
