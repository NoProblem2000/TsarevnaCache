package io.github.noproblem2000.tsarevnacache.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.config.JsonFiles;
import io.github.noproblem2000.tsarevnacache.config.properties.DtoSchemaProperties;
import io.github.noproblem2000.tsarevnacache.dto.DtoClassSchema.SchemaKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Registry of dynamic DTO schemas, persisted in {@code config/dto-schemas.json}.
 *
 * <p>The file holds two independent parts: the factory mapping parsed from
 * {@code DataSerializableFactory.java} ({@code factoryId}, {@code factoryConstants},
 * {@code factoryClasses}) and the class schemas. Schemas are stored strictly under
 * {@code factoryId:classId}; adding, replacing or deleting schemas never touches the factory
 * mapping, and parsing the factory never touches the schemas (it only links their nested classes).
 *
 * <p>Nested DTO references ({@code NULLABLE_OBJECT} / {@code NULLABLE_LIST} fields with a
 * {@code nestedClassName}) are linked independently of the order in which schemas are added: every
 * change re-resolves the {@code nestedClassId} of all schemas from the registered schemas and the
 * factory mapping.
 *
 * <p>Loaded once on start (an empty registry with factory id {@code 1} when the file does not
 * exist). Every change is validated and written back to the file atomically. Reads are lock-free
 * for the serialization hot path ({@link #find(int, int)} is called for every DTO read).
 */
@Slf4j
@Service
public class DtoSchemaRegistryService {

    private final Path file;
    private final ObjectMapper mapper;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile Snapshot snapshot;
    private volatile Set<Integer> activeFactoryIds = Set.of();
    private volatile Map<Integer, String> libraryFactories = Map.of();

    public DtoSchemaRegistryService(DtoSchemaProperties properties, ObjectMapper objectMapper) {
        this.file = Path.of(properties.getFile()).toAbsolutePath().normalize();
        this.mapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.snapshot = Snapshot.of(load());
        log.info("DTO schema registry {}: {} schema(s), factory id {}, {} factory constant(s)", file,
                snapshot.schemas.size(), snapshot.factoryId, snapshot.factoryConstants.size());
    }

    /**
     * Name used to match class names and factory constants regardless of case and underscores:
     * {@code SOME_DTO}, {@code SOMEDTO} and {@code SomeDTO} all become {@code somedto}.
     */
    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace("_", "");
    }

    // ------------------------------------------------------------------ queries

    /** Schema for a factory/class id pair. */
    public Optional<DtoClassSchema> find(int factoryId, int classId) {
        return Optional.ofNullable(snapshot.schemas.get(new SchemaKey(factoryId, classId)));
    }

    /** First schema with the given simple class name (exact match first, then case/underscore insensitive). */
    public Optional<DtoClassSchema> findByClassName(String className) {
        return findByClassName(snapshot, className, null);
    }

    /** All schemas, ordered by factory id and class id. */
    public List<DtoClassSchema> list() {
        return List.copyOf(snapshot.schemas.values());
    }

    /** Id of the project's factory ({@code FACTORY_ID}). */
    public int factoryId() {
        return snapshot.factoryId;
    }

    public Map<String, Integer> factoryConstants() {
        return snapshot.factoryConstants;
    }

    public Map<String, Integer> factoryClasses() {
        return snapshot.factoryClasses;
    }

    /**
     * Class id for a class name: from the factory mapping ({@code case X -> new SomeDTO()}), then a
     * factory constant with the same normalized name ({@code SOMEDTO} for {@code SomeDTO}), then a
     * registered schema of the project factory.
     */
    public Optional<Integer> resolveClassId(String className) {
        return resolveClassId(snapshot, className);
    }

    /**
     * Class id for a factory constant such as {@code SOMEDTO}; matched exactly, then without regard
     * to case and underscores ({@code DataSerializableFactory.SomeDTO}), then against the class
     * names of the factory mapping.
     */
    public Optional<Integer> resolveConstant(String constant) {
        if (constant == null || constant.isBlank()) {
            return Optional.empty();
        }
        Snapshot current = snapshot;
        Integer exact = current.factoryConstants.get(constant.trim());
        if (exact != null) {
            return Optional.of(exact);
        }
        Optional<Integer> normalized = byNormalizedName(current.factoryConstants, constant);
        return normalized.isPresent() ? normalized : byNormalizedName(current.factoryClasses, constant);
    }

    /** Class name for a class id: from its schema, else from the factory mapping (project factory only). */
    public Optional<String> classNameFor(int factoryId, int classId) {
        Optional<String> fromSchema = find(factoryId, classId).map(DtoClassSchema::className);
        if (fromSchema.isPresent() || factoryId != snapshot.factoryId) {
            return fromSchema;
        }
        return snapshot.factoryClasses.entrySet().stream()
                .filter(entry -> entry.getValue() == classId)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** Factory ids of all registered schemas plus the project's factory id. */
    public Set<Integer> factoryIds() {
        Set<Integer> ids = new HashSet<>();
        ids.add(snapshot.factoryId);
        snapshot.schemas.keySet().forEach(key -> ids.add(key.factoryId()));
        return ids;
    }

    /** Factory ids for which a dynamic factory is registered in the running Hazelcast client. */
    public Set<Integer> activeFactoryIds() {
        return activeFactoryIds;
    }

    public void setActiveFactoryIds(Set<Integer> ids) {
        this.activeFactoryIds = Set.copyOf(ids);
    }

    public boolean isActiveFactory(int factoryId) {
        return activeFactoryIds.contains(factoryId);
    }

    /**
     * {@code DataSerializableFactory} classes of the running client (configured or discovered in
     * {@code lib/}): factory id to class name. For a dynamic factory id it is the fallback for class
     * ids without a schema.
     */
    public Map<Integer, String> libraryFactories() {
        return libraryFactories;
    }

    public void setLibraryFactories(Map<Integer, String> factories) {
        this.libraryFactories = Collections.unmodifiableMap(new TreeMap<>(factories));
    }

    /** Location of the registry file. */
    public Path file() {
        return file;
    }

    // ------------------------------------------------------------------ changes

    /** Adds or replaces the schema stored under its {@code factoryId:classId} and saves the file. */
    public DtoClassSchema save(DtoClassSchema schema) {
        return saveDetailed(schema).schema();
    }

    /**
     * Adds or replaces the schema stored under its {@code factoryId:classId}, links nested class
     * references in both directions and saves the file. Other schemas are never removed, even when
     * they use the same class name.
     */
    public SaveOutcome saveDetailed(DtoClassSchema schema) {
        DtoClassSchema validated = validate(schema);
        lock.writeLock().lock();
        try {
            Snapshot current = snapshot;
            Map<SchemaKey, DtoClassSchema> schemas = new TreeMap<>(current.schemas);
            schemas.put(validated.key(), validated);
            Snapshot next = current.withSchemas(schemas);
            Map<SchemaKey, DtoClassSchema> linked = linkNested(next);
            next = next.withSchemas(linked);

            List<SchemaKey> updated = new ArrayList<>();
            for (Map.Entry<SchemaKey, DtoClassSchema> entry : linked.entrySet()) {
                DtoClassSchema before = current.schemas.get(entry.getKey());
                if (!entry.getKey().equals(validated.key()) && before != null && !before.equals(entry.getValue())) {
                    updated.add(entry.getKey());
                }
            }
            List<String> warnings = new ArrayList<>();
            for (DtoClassSchema other : current.schemas.values()) {
                if (!other.key().equals(validated.key()) && other.factoryId().equals(validated.factoryId())
                        && other.className().equals(validated.className())) {
                    warnings.add("Class " + validated.className() + " is also registered with classId " + other.classId()
                            + "; delete that schema if the class was renumbered");
                }
            }
            replace(next);
            return new SaveOutcome(linked.get(validated.key()), updated, warnings);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Adds or replaces several schemas at once (each under its {@code factoryId:classId}), links the
     * nested class references of all schemas once at the end and writes the file once. The order of
     * {@code batch} does not matter: a parent and its nested classes in the same batch are linked to
     * each other.
     *
     * @throws ApiException with {@link ErrorCode#DTO_SCHEMA_INVALID} when one schema is invalid; nothing is saved then
     */
    public BatchOutcome saveAll(List<DtoClassSchema> batch) {
        List<DtoClassSchema> validated = batch.stream().map(this::validate).toList();
        lock.writeLock().lock();
        try {
            Snapshot current = snapshot;
            Map<SchemaKey, DtoClassSchema> schemas = new TreeMap<>(current.schemas);
            validated.forEach(schema -> schemas.put(schema.key(), schema));
            Snapshot next = current.withSchemas(schemas);
            Map<SchemaKey, DtoClassSchema> linked = linkNested(next);
            next = next.withSchemas(linked);

            Set<SchemaKey> batchKeys = new HashSet<>();
            validated.forEach(schema -> batchKeys.add(schema.key()));
            List<DtoClassSchema> saved = validated.stream().map(schema -> linked.get(schema.key())).toList();
            List<SchemaKey> replaced = validated.stream().map(DtoClassSchema::key).filter(current.schemas::containsKey).toList();
            List<SchemaKey> relinked = new ArrayList<>();
            for (Map.Entry<SchemaKey, DtoClassSchema> entry : linked.entrySet()) {
                DtoClassSchema before = current.schemas.get(entry.getKey());
                if (!batchKeys.contains(entry.getKey()) && before != null && !before.equals(entry.getValue())) {
                    relinked.add(entry.getKey());
                }
            }
            if (!validated.isEmpty()) {
                replace(next);
            }
            return new BatchOutcome(saved, replaced, relinked);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Removes a schema; {@code false} when it did not exist. The factory mapping is kept. */
    public boolean delete(int factoryId, int classId) {
        lock.writeLock().lock();
        try {
            Map<SchemaKey, DtoClassSchema> schemas = new TreeMap<>(snapshot.schemas);
            if (schemas.remove(new SchemaKey(factoryId, classId)) == null) {
                return false;
            }
            replace(snapshot.withSchemas(schemas));
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Replaces the factory mapping (parsed from {@code DataSerializableFactory.java}) and links the
     * nested class references of the existing schemas. The schemas themselves are kept.
     */
    public void updateFactory(int factoryId, Map<String, Integer> constants, Map<String, Integer> classes) {
        lock.writeLock().lock();
        try {
            Snapshot next = new Snapshot(factoryId, new LinkedHashMap<>(constants), new LinkedHashMap<>(classes), snapshot.schemas);
            replace(next.withSchemas(linkNested(next)));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Current content as written to {@code dto-schemas.json}. */
    public DtoSchemaRegistryFile export() {
        Snapshot current = snapshot;
        return new DtoSchemaRegistryFile(DtoSchemaRegistryFile.CURRENT_FORMAT, current.factoryId,
                current.factoryConstants, current.factoryClasses, List.copyOf(current.schemas.values()));
    }

    /**
     * Imports a registry file.
     *
     * @param replace {@code true}: the file's schemas replace all schemas, and its factory mapping
     *                replaces the current one when the file contains a mapping (a file with schemas only,
     *                e.g. the export of a single schema, keeps the current factory mapping);
     *                {@code false}: its schemas and factory entries are merged into the registry
     */
    public DtoSchemaRegistryFile importFile(DtoSchemaRegistryFile content, boolean replace) {
        List<DtoClassSchema> validated = new ArrayList<>();
        for (DtoClassSchema schema : content.schemas()) {
            validated.add(validate(schema));
        }
        lock.writeLock().lock();
        try {
            Snapshot current = snapshot;
            Map<SchemaKey, DtoClassSchema> schemas = replace ? new TreeMap<>() : new TreeMap<>(current.schemas);
            validated.forEach(schema -> schemas.put(schema.key(), schema));
            boolean fileHasMapping = !content.factoryConstants().isEmpty() || !content.factoryClasses().isEmpty();
            boolean replaceMapping = replace && fileHasMapping;
            Map<String, Integer> constants = replaceMapping ? new LinkedHashMap<>() : new LinkedHashMap<>(current.factoryConstants);
            constants.putAll(content.factoryConstants());
            Map<String, Integer> classes = replaceMapping ? new LinkedHashMap<>() : new LinkedHashMap<>(current.factoryClasses);
            classes.putAll(content.factoryClasses());
            int factory = replaceMapping ? content.factoryId() : current.factoryId;
            Snapshot next = new Snapshot(factory, constants, classes, schemas);
            replace(next.withSchemas(linkNested(next)));
            return export();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ------------------------------------------------------------------ nested links

    /**
     * Resolves {@code nestedClassId} of every nested field from its {@code nestedClassName}: a
     * registered schema of the same factory with that class name wins, then the factory mapping
     * (project factory only). A field whose class is not known yet keeps its current id (or none)
     * and is linked as soon as the class is registered.
     */
    private static Map<SchemaKey, DtoClassSchema> linkNested(Snapshot state) {
        Map<SchemaKey, DtoClassSchema> result = new TreeMap<>();
        for (DtoClassSchema schema : state.schemas.values()) {
            List<DtoFieldSchema> fields = new ArrayList<>(schema.fields().size());
            boolean changed = false;
            for (DtoFieldSchema field : schema.fields()) {
                DtoFieldSchema resolved = linkField(state, schema.factoryId(), field);
                changed |= resolved != field;
                fields.add(resolved);
            }
            result.put(schema.key(), changed
                    ? new DtoClassSchema(schema.factoryId(), schema.classId(), schema.className(), fields, schema.rawJavaSource())
                    : schema);
        }
        return result;
    }

    private static DtoFieldSchema linkField(Snapshot state, int factoryId, DtoFieldSchema field) {
        if (!field.fieldType().hasNestedObjects() || field.nestedClassName() == null || field.nestedClassName().isBlank()) {
            return field;
        }
        String nestedName = simpleName(field.nestedClassName());
        Integer classId = findByClassName(state, nestedName, factoryId).map(DtoClassSchema::classId).orElse(null);
        if (classId == null && factoryId == state.factoryId) {
            classId = resolveFromFactory(state, nestedName).orElse(null);
        }
        if (classId == null || classId.equals(field.nestedClassId())) {
            return field;
        }
        return new DtoFieldSchema(field.order(), field.fieldName(), field.fieldType(), classId, field.nestedClassName());
    }

    // ------------------------------------------------------------------ name resolution

    private static Optional<Integer> resolveClassId(Snapshot state, String className) {
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }
        String name = simpleName(className);
        Optional<Integer> fromFactory = resolveFromFactory(state, name);
        if (fromFactory.isPresent()) {
            return fromFactory;
        }
        return findByClassName(state, name, state.factoryId).map(DtoClassSchema::classId);
    }

    /** Factory mapping: class name exactly, class name normalized, then a constant with the normalized class name. */
    private static Optional<Integer> resolveFromFactory(Snapshot state, String className) {
        Integer exact = state.factoryClasses.get(className);
        if (exact != null) {
            return Optional.of(exact);
        }
        Optional<Integer> byClass = byNormalizedName(state.factoryClasses, className);
        return byClass.isPresent() ? byClass : byNormalizedName(state.factoryConstants, className);
    }

    private static Optional<Integer> byNormalizedName(Map<String, Integer> names, String name) {
        String wanted = normalize(simpleName(name));
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        return names.entrySet().stream()
                .filter(entry -> normalize(entry.getKey()).equals(wanted))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /**
     * Schema by class name (exact first, then normalized).
     *
     * @param factoryId only schemas of this factory, or {@code null} for any factory
     */
    private static Optional<DtoClassSchema> findByClassName(Snapshot state, String className, Integer factoryId) {
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }
        String name = simpleName(className);
        List<DtoClassSchema> candidates = state.schemas.values().stream()
                .filter(schema -> factoryId == null || schema.factoryId().equals(factoryId))
                .toList();
        Optional<DtoClassSchema> exact = candidates.stream().filter(schema -> schema.className().equals(name)).findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        String wanted = normalize(name);
        return candidates.stream().filter(schema -> normalize(schema.className()).equals(wanted)).findFirst();
    }

    // ------------------------------------------------------------------ validation

    /**
     * Checks a schema and returns it normalized (fields sorted). A missing class id is never
     * replaced by a default.
     *
     * @throws ApiException with {@link ErrorCode#DTO_SCHEMA_INVALID}
     */
    public DtoClassSchema validate(DtoClassSchema schema) {
        if (schema == null) {
            throw invalid("Schema is missing", Map.of());
        }
        if (schema.classId() == null) {
            throw invalid("classId is required (parse the DataSerializableFactory first or enter it manually)",
                    Map.of("className", String.valueOf(schema.className())));
        }
        if (schema.className() == null || !schema.className().matches("[A-Za-z_$][\\w$]*")) {
            throw invalid("className must be a simple Java class name", Map.of("className", String.valueOf(schema.className())));
        }
        Set<String> names = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (DtoFieldSchema field : schema.fields()) {
            if (field.fieldName() == null || field.fieldName().isBlank()) {
                throw invalid("Every field needs a name", Map.of("order", field.order()));
            }
            if (field.fieldName().startsWith("@")) {
                throw invalid("Field names must not start with '@'", Map.of("field", field.fieldName()));
            }
            if (field.fieldType() == null) {
                throw invalid("Field '" + field.fieldName() + "' has no type", Map.of("field", field.fieldName()));
            }
            if (!names.add(field.fieldName())) {
                throw invalid("Duplicate field '" + field.fieldName() + "'", Map.of("field", field.fieldName()));
            }
            if (!orders.add(field.order())) {
                throw invalid("Duplicate field order " + field.order(), Map.of("order", field.order()));
            }
        }
        return new DtoClassSchema(schema.factoryId(), schema.classId(), schema.className(), schema.fields(), schema.rawJavaSource());
    }

    private static ApiException invalid(String message, Map<String, Object> details) {
        return new ApiException(ErrorCode.DTO_SCHEMA_INVALID, message, details);
    }

    // ------------------------------------------------------------------ persistence

    private DtoSchemaRegistryFile load() {
        if (!Files.exists(file)) {
            return DtoSchemaRegistryFile.empty();
        }
        try {
            DtoSchemaRegistryFile content = mapper.readValue(file.toFile(), DtoSchemaRegistryFile.class);
            List<DtoClassSchema> valid = new ArrayList<>();
            for (DtoClassSchema schema : content.schemas()) {
                if (schema.classId() == null || schema.className() == null) {
                    log.warn("Ignoring incomplete schema in {}: {}", file, schema);
                } else {
                    valid.add(schema);
                }
            }
            return new DtoSchemaRegistryFile(content.formatVersion(), content.factoryId(),
                    content.factoryConstants(), content.factoryClasses(), valid);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the DTO schema registry " + file + ": " + e.getMessage(), e);
        }
    }

    private void replace(Snapshot next) {
        persist(next);
        snapshot = next;
    }

    private void persist(Snapshot content) {
        try {
            JsonFiles.writeAtomically(mapper, file, new DtoSchemaRegistryFile(DtoSchemaRegistryFile.CURRENT_FORMAT,
                    content.factoryId, content.factoryConstants, content.factoryClasses,
                    List.copyOf(content.schemas.values())), "dto-schemas");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the DTO schema registry " + file, e);
        }
    }

    private static String simpleName(String className) {
        String trimmed = className.trim();
        int dot = trimmed.lastIndexOf('.');
        return dot >= 0 ? trimmed.substring(dot + 1) : trimmed;
    }

    /**
     * Result of {@link #saveDetailed}.
     *
     * @param schema   the stored schema (nested class ids linked)
     * @param linked   other schemas whose nested class ids were filled in because of this schema
     * @param warnings things to check, e.g. the same class registered under another class id
     */
    public record SaveOutcome(DtoClassSchema schema, List<SchemaKey> linked, List<String> warnings) {
    }

    /**
     * Result of {@link #saveAll}.
     *
     * @param saved    the stored schemas, in batch order (nested class ids linked)
     * @param replaced keys of the batch that already had a schema (updated rather than added)
     * @param relinked other, already registered schemas whose nested class ids were filled in
     */
    public record BatchOutcome(List<DtoClassSchema> saved, List<SchemaKey> replaced, List<SchemaKey> relinked) {
    }

    /** Immutable registry state, swapped atomically on every change. */
    private record Snapshot(int factoryId, Map<String, Integer> factoryConstants, Map<String, Integer> factoryClasses,
                            Map<SchemaKey, DtoClassSchema> schemas) {

        Snapshot {
            factoryConstants = Collections.unmodifiableMap(new LinkedHashMap<>(factoryConstants));
            factoryClasses = Collections.unmodifiableMap(new LinkedHashMap<>(factoryClasses));
            schemas = Collections.unmodifiableMap(new TreeMap<>(schemas));
        }

        Snapshot withSchemas(Map<SchemaKey, DtoClassSchema> next) {
            return new Snapshot(factoryId, factoryConstants, factoryClasses, next);
        }

        static Snapshot of(DtoSchemaRegistryFile content) {
            Map<SchemaKey, DtoClassSchema> schemas = new TreeMap<>();
            content.schemas().forEach(schema -> schemas.put(schema.key(), schema));
            Snapshot loaded = new Snapshot(content.factoryId(), content.factoryConstants(), content.factoryClasses(), schemas);
            return loaded.withSchemas(linkNested(loaded));
        }
    }
}
