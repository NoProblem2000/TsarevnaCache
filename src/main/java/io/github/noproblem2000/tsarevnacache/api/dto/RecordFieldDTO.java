package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hazelcast.nio.serialization.FieldKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A single field of a {@link RecordSchemaDTO}.
 *
 * @param name   field name
 * @param kind   Hazelcast field kind, e.g. {@code INT32}, {@code STRING}, {@code COMPACT}
 * @param schema schema of nested records for {@code COMPACT}/{@code ARRAY_OF_COMPACT} (and the
 *               Portable equivalents); {@code null} when unknown because the nested value is null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecordFieldDTO(
        @NotBlank String name,
        @NotNull FieldKind kind,
        @Valid RecordSchemaDTO schema) {
}
