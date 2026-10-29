package io.github.noproblem2000.tsarevnacache.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.config.properties.DtoSchemaProperties;
import io.github.noproblem2000.tsarevnacache.dto.DtoClassSchema.SchemaKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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

    public DtoSchemaRegistryService(DtoSchemaProperties properties, ObjectMapper objectMapper) {
        this.file = Path.of(properties.getFile()).toAbsolutePath().normalize();
        this.mapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.snapshot = Snapshot.of(load());
        log.info("DTO schema registry {}: {} schema(s), factory id {}", file, snapshot.schemas.size(), snapshot.factoryId);
    }

    // ------------------------------------------------------------------ queries

    /** Schema for a factory/class id pair. */
    public Optional<DtoClassSchema> find(int factoryId, int classId) {
        return Optional.ofNullable(snapshot.schemas.get(new SchemaKey(factoryId, classId)));
    }

    /** First schema with the given simple class name. */
    public Optional<DtoClassSchema> findByClassName(String className) {
        if (className == null) {
            return Optional.empty();
        }
        String name = simpleName(className);
        return snapshot.schemas.values().stream().filter(schema -> schema.className().equals(name)).findFirst();
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

    /** Class id for a class name: from the factory mapping, else from a registered schema. */
    public Optional<Integer> resolveClassId(String className) {
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }
        String name = simpleName(className);
        Integer fromFactory = snapshot.factoryClasses.get(name);
        if (fromFactory != null) {
            return Optional.of(fromFactory);
        }
        return findByClassName(name).map(DtoClassSchema::classId);
    }

    /** Class id for a factory constant name such as {@code SOMEDTO}. */
    public Optional<Integer> resolveConstant(String constant) {
        return Optional.ofNullable(constant == null ? null : snapshot.factoryConstants.get(constant));
    }

    /** Class name for a class id of the project's factory, used to name missing schemas. */
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

    /** Location of the registry file. */
    public Path file() {
        return file;
    }

    // ------------------------------------------------------------------ changes

    /** Adds or replaces a schema (same factory id + class id) and saves the file. */
    public DtoClassSchema save(DtoClassSchema schema) {
        DtoClassSchema validated = validate(schema);
        lock.writeLock().lock();
        try {
            Map<SchemaKey, DtoClassSchema> schemas = new TreeMap<>(snapshot.schemas);
            schemas.values().removeIf(existing -> existing.className().equals(validated.className())
                    && existing.factoryId().equals(validated.factoryId())
                    && !existing.classId().equals(validated.classId()));
            schemas.put(validated.key(), validated);
            replace(new Snapshot(snapshot.factoryId, snapshot.factoryConstants, snapshot.factoryClasses, schemas));
            return validated;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Removes a schema; {@code false} when it did not exist. */
    public boolean delete(int factoryId, int classId) {
        lock.writeLock().lock();
        try {
            Map<SchemaKey, DtoClassSchema> schemas = new TreeMap<>(snapshot.schemas);
            if (schemas.remove(new SchemaKey(factoryId, classId)) == null) {
                return false;
            }
            replace(new Snapshot(snapshot.factoryId, snapshot.factoryConstants, snapshot.factoryClasses, schemas));
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Replaces the factory mapping (parsed from {@code DataSerializableFactory.java}) and fills in
     * missing nested class ids of existing schemas.
     */
    public void updateFactory(int factoryId, Map<String, Integer> constants, Map<String, Integer> classes) {
        lock.writeLock().lock();
        try {
            Map<String, Integer> classMap = new LinkedHashMap<>(classes);
            Map<SchemaKey, DtoClassSchema> schemas = new TreeMap<>();
            for (DtoClassSchema schema : snapshot.schemas.values()) {
                DtoClassSchema resolved = schema.factoryId() == factoryId ? resolveNested(schema, classMap) : schema;
                schemas.put(resolved.key(), resolved);
            }
            replace(new Snapshot(factoryId, new LinkedHashMap<>(constants), classMap, schemas));
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
     * @param replace {@code true}: the file replaces the registry; {@code false}: its schemas and
     *                factory entries are merged into the registry
     */
    public DtoSchemaRegistryFile importFile(DtoSchemaRegistryFile content, boolean replace) {
        List<DtoClassSchema> validated = new ArrayList<>();
        for (DtoClassSchema schema : content.schemas()) {
            validated.add(validate(schema));
        }
        lock.writeLock().lock();
        try {
            Map<SchemaKey, DtoClassSchema> schemas = replace ? new TreeMap<>() : new TreeMap<>(snapshot.schemas);
            validated.forEach(schema -> schemas.put(schema.key(), schema));
            Map<String, Integer> constants = replace ? new LinkedHashMap<>() : new LinkedHashMap<>(snapshot.factoryConstants);
            constants.putAll(content.factoryConstants());
            Map<String, Integer> classes = replace ? new LinkedHashMap<>() : new LinkedHashMap<>(snapshot.factoryClasses);
            classes.putAll(content.factoryClasses());
            int factory = replace ? content.factoryId() : snapshot.factoryId;
            replace(new Snapshot(factory, constants, classes, schemas));
            return export();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ------------------------------------------------------------------ validation

    /**
     * Checks a schema and returns it normalized (fields sorted, nested ids resolved).
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
        return resolveNested(schema, snapshot.factoryClasses);
    }

    private static DtoClassSchema resolveNested(DtoClassSchema schema, Map<String, Integer> classes) {
        List<DtoFieldSchema> fields = schema.fields().stream()
                .map(field -> field.fieldType().hasNestedObjects() && field.nestedClassId() == null && field.nestedClassName() != null
                        && classes.containsKey(field.nestedClassName())
                        ? new DtoFieldSchema(field.order(), field.fieldName(), field.fieldType(),
                        classes.get(field.nestedClassName()), field.nestedClassName())
                        : field)
                .toList();
        return new DtoClassSchema(schema.factoryId(), schema.classId(), schema.className(), fields, schema.rawJavaSource());
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
            Path directory = file.getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            Path temp = Files.createTempFile(directory != null ? directory : Path.of("."), "dto-schemas", ".tmp");
            mapper.writeValue(temp.toFile(), new DtoSchemaRegistryFile(DtoSchemaRegistryFile.CURRENT_FORMAT,
                    content.factoryId, content.factoryConstants, content.factoryClasses,
                    List.copyOf(content.schemas.values())));
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the DTO schema registry " + file, e);
        }
    }

    private static String simpleName(String className) {
        String trimmed = className.trim();
        int dot = trimmed.lastIndexOf('.');
        return dot >= 0 ? trimmed.substring(dot + 1) : trimmed;
    }

    /** Immutable registry state, swapped atomically on every change. */
    private record Snapshot(int factoryId, Map<String, Integer> factoryConstants, Map<String, Integer> factoryClasses,
                            Map<SchemaKey, DtoClassSchema> schemas) {

        Snapshot {
            factoryConstants = Collections.unmodifiableMap(new LinkedHashMap<>(factoryConstants));
            factoryClasses = Collections.unmodifiableMap(new LinkedHashMap<>(factoryClasses));
            schemas = Collections.unmodifiableMap(new TreeMap<>(schemas));
        }

        static Snapshot of(DtoSchemaRegistryFile content) {
            Map<SchemaKey, DtoClassSchema> schemas = new TreeMap<>();
            content.schemas().forEach(schema -> schemas.put(schema.key(), schema));
            return new Snapshot(content.factoryId(), content.factoryConstants(), content.factoryClasses(), schemas);
        }
    }
}
