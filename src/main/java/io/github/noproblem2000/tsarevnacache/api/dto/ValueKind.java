package io.github.noproblem2000.tsarevnacache.api.dto;

/**
 * Display category of an inspected key or value.
 */
public enum ValueKind {
    NULL,
    STRING,
    NUMBER,
    BOOLEAN,
    UUID,
    /** {@code HazelcastJsonValue}. */
    JSON,
    /** Compact {@code GenericRecord} (class not on the admin classpath). */
    COMPACT,
    /** Portable {@code GenericRecord}. */
    PORTABLE,
    /** A Java object converted to JSON with Jackson. */
    OBJECT,
    COLLECTION,
    MAP,
    ARRAY,
    BYTES,
    DATE_TIME,
    /** The value could not be deserialized; see {@link UnresolvedObjectDTO}. */
    UNRESOLVED
}
