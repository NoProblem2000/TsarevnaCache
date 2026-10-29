package io.github.noproblem2000.tsarevnacache.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * One field of a {@link DtoClassSchema}, in the order of {@code readData}/{@code writeData}.
 *
 * @param order           position in the binary layout (0, 1, 2, ...)
 * @param fieldName       Java field name; also the JSON property name
 * @param fieldType       wire format
 * @param nestedClassId   class id of nested DTOs ({@code NULLABLE_OBJECT}, {@code NULLABLE_LIST}); used
 *                        when JSON without an explicit {@code @classId} is written back
 * @param nestedClassName simple class name of nested DTOs, resolved to a class id through the factory
 *                        mapping when {@code nestedClassId} is not set
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DtoFieldSchema(
        @Min(0) int order,
        @NotBlank String fieldName,
        @NotNull DtoFieldType fieldType,
        Integer nestedClassId,
        String nestedClassName) {
}
