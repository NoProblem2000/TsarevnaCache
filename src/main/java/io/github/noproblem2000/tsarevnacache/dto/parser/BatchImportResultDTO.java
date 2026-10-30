package io.github.noproblem2000.tsarevnacache.dto.parser;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.noproblem2000.tsarevnacache.dto.DtoClassSchema;
import java.util.List;

/**
 * Result of {@code POST /api/schemas/batch-import-java}.
 *
 * @param totalFiles           number of files received
 * @param importedCount        number of DTO schemas saved
 * @param imported             the saved schemas (nested class ids linked), ordered by factory id and class id
 * @param updatedKeys          keys ({@code factoryId:classId}) of imported schemas that replaced an existing schema
 * @param linkedSchemas        keys of schemas outside the batch whose nested fields now point to an imported class
 * @param factoryUpdated       {@code true} when a {@code DataSerializableFactory} of the batch replaced the factory mapping
 * @param factoryFile          file the factory mapping was taken from
 * @param factoryId            {@code FACTORY_ID} of that factory
 * @param factoryConstantCount number of class id constants of that factory
 * @param ignoredCount         number of skipped files
 * @param ignored              skipped files with the reason
 * @param errors               problems in valid DTOs (unrecognized fields, DTOs that could not be imported)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchImportResultDTO(
        int totalFiles,
        int importedCount,
        List<DtoClassSchema> imported,
        List<String> updatedKeys,
        List<String> linkedSchemas,
        boolean factoryUpdated,
        String factoryFile,
        Integer factoryId,
        int factoryConstantCount,
        int ignoredCount,
        List<IgnoredFileDTO> ignored,
        List<BatchImportErrorDTO> errors) {
}
