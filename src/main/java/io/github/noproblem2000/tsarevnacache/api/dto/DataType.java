package io.github.noproblem2000.tsarevnacache.api.dto;

/**
 * Types a key or value can be written as through the API.
 *
 * <p>On read, the inspector reports the matching type (if any) so the UI can pre-select it when
 * editing an entry.
 */
public enum DataType {

    STRING,
    INTEGER,
    LONG,
    SHORT,
    BYTE,
    DOUBLE,
    FLOAT,
    BOOLEAN,
    UUID,
    BIG_DECIMAL,
    BIG_INTEGER,

    /** Stored as {@code HazelcastJsonValue}, queryable by SQL predicates without any class. */
    JSON,

    /** JSON bound to a Java class (from the admin or DTO classpath) via Jackson before the put. */
    CLASS,

    /** Stored as a Compact {@code GenericRecord} built from JSON and a {@link RecordSchemaDTO}. */
    COMPACT
}
