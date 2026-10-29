package io.github.noproblem2000.tsarevnacache.api;

import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.dto.DtoClassSchema;
import io.github.noproblem2000.tsarevnacache.dto.DtoSchemaRegistryFile;
import io.github.noproblem2000.tsarevnacache.dto.DtoSchemaRegistryService;
import io.github.noproblem2000.tsarevnacache.dto.parser.DtoParseResult;
import io.github.noproblem2000.tsarevnacache.dto.parser.FactoryParseResult;
import io.github.noproblem2000.tsarevnacache.dto.parser.JavaDtoSourceParserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dynamic DTO schema registry: list, parse Java sources, save, delete, import and export
 * {@code config/dto-schemas.json}.
 */
@Validated
@RestController
@RequestMapping("/api/schemas")
@RequiredArgsConstructor
public class SchemaController {

    private final DtoSchemaRegistryService registry;
    private final JavaDtoSourceParserService parser;

    /** All schemas plus the factory mapping. */
    @GetMapping
    public RegistryView list() {
        return view();
    }

    /**
     * Parses a pasted {@code DataSerializableFactory.java} and, unless {@code save=false}, stores the
     * factory id and the constant / class name to class id mapping.
     */
    @PostMapping("/parse-factory")
    public FactoryParseResult parseFactory(@Valid @RequestBody SourceRequest request,
                                           @RequestParam(defaultValue = "true") boolean save) {
        FactoryParseResult result = parser.parseFactorySource(request.source());
        if (save) {
            registry.updateFactory(result.factoryId(), result.constants(), result.classes());
        }
        return result;
    }

    /** Parses a pasted DTO class; returns a preview that is not saved. */
    @PostMapping("/parse-dto")
    public DtoParseResult parseDto(@Valid @RequestBody SourceRequest request) {
        return parser.parseDtoClassSource(request.source());
    }

    /** Creates or replaces a schema (same factory id + class id). */
    @PostMapping
    public SaveResult save(@Valid @RequestBody DtoClassSchema schema) {
        DtoClassSchema saved = registry.save(schema);
        return new SaveResult(saved, !registry.isActiveFactory(saved.factoryId()));
    }

    @DeleteMapping("/{factoryId}/{classId}")
    public ResponseEntity<Void> delete(@PathVariable int factoryId, @PathVariable int classId) {
        if (!registry.delete(factoryId, classId)) {
            throw new ApiException(ErrorCode.DTO_SCHEMA_NOT_FOUND, "No DTO schema for factoryId=" + factoryId
                    + ", classId=" + classId, Map.of("factoryId", factoryId, "classId", classId));
        }
        return ResponseEntity.noContent().build();
    }

    /** Downloads the registry as {@code dto-schemas.json}. */
    @GetMapping("/export")
    public ResponseEntity<DtoSchemaRegistryFile> export() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("dto-schemas.json").build().toString())
                .body(registry.export());
    }

    /**
     * Uploads a {@code dto-schemas.json}.
     *
     * @param mode {@code replace} (default) replaces the registry, {@code merge} adds/overwrites
     */
    @PostMapping("/import")
    public RegistryView importFile(@RequestBody DtoSchemaRegistryFile content,
                                   @RequestParam(defaultValue = "replace") String mode) {
        if (!mode.equals("replace") && !mode.equals("merge")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "mode must be 'replace' or 'merge'", Map.of("mode", mode));
        }
        registry.importFile(content, mode.equals("replace"));
        return view();
    }

    private RegistryView view() {
        Set<Integer> inactive = new TreeSet<>(registry.factoryIds());
        inactive.removeAll(registry.activeFactoryIds());
        return new RegistryView(registry.factoryId(), registry.factoryConstants(), registry.factoryClasses(),
                new TreeSet<>(registry.activeFactoryIds()), inactive, registry.libraryFactories(), registry.file().toString(),
                registry.list());
    }

    /**
     * Pasted Java source.
     *
     * @param source content of a {@code .java} file
     */
    public record SourceRequest(@NotBlank String source) {
    }

    /**
     * Result of saving a schema.
     *
     * @param schema          the stored (normalized) schema
     * @param restartRequired {@code true} when its factory id is not served by the running client yet;
     *                        the dynamic factory for it is registered on the next start
     */
    public record SaveResult(DtoClassSchema schema, boolean restartRequired) {
    }

    /**
     * Registry content.
     *
     * @param factoryId         id of the project's factory
     * @param factoryConstants  constant name to class id
     * @param factoryClasses    class name to class id
     * @param activeFactoryIds  factory ids served by the running Hazelcast client
     * @param pendingFactoryIds factory ids of schemas that need a restart to become readable
     * @param libraryFactories  {@code DataSerializableFactory} classes of the client (configured or found in
     *                          {@code lib/}); for a dynamic factory id the fallback of class ids without a schema
     * @param file              location of {@code dto-schemas.json}
     * @param schemas           all schemas
     */
    public record RegistryView(
            int factoryId,
            Map<String, Integer> factoryConstants,
            Map<String, Integer> factoryClasses,
            Set<Integer> activeFactoryIds,
            Set<Integer> pendingFactoryIds,
            Map<Integer, String> libraryFactories,
            String file,
            List<DtoClassSchema> schemas) {
    }
}
