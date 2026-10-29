package io.github.noproblem2000.tsarevnacache.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Content of {@code config/dto-schemas.json}: the factory mapping and all DTO schemas.
 *
 * @param formatVersion    file format version
 * @param factoryId        id of the project's {@code DataSerializableFactory} ({@code FACTORY_ID})
 * @param factoryConstants factory constants, e.g. {@code SOMEDTO -> 1}
 * @param factoryClasses   class names created by the factory, e.g. {@code SomeDTO -> 1}
 * @param schemas          DTO schemas
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DtoSchemaRegistryFile(
        int formatVersion,
        Integer factoryId,
        Map<String, Integer> factoryConstants,
        Map<String, Integer> factoryClasses,
        List<DtoClassSchema> schemas) {

    public static final int CURRENT_FORMAT = 1;

    public DtoSchemaRegistryFile {
        formatVersion = formatVersion <= 0 ? CURRENT_FORMAT : formatVersion;
        factoryId = factoryId == null ? DtoClassSchema.DEFAULT_FACTORY_ID : factoryId;
        factoryConstants = factoryConstants == null ? new LinkedHashMap<>() : new LinkedHashMap<>(factoryConstants);
        factoryClasses = factoryClasses == null ? new LinkedHashMap<>() : new LinkedHashMap<>(factoryClasses);
        schemas = schemas == null ? List.of() : List.copyOf(schemas);
    }

    public static DtoSchemaRegistryFile empty() {
        return new DtoSchemaRegistryFile(CURRENT_FORMAT, DtoClassSchema.DEFAULT_FACTORY_ID, null, null, null);
    }
}
