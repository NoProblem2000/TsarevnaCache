package io.github.noproblem2000.tsarevnacache.api.dto;

/**
 * Why a stored object could not be deserialized. The frontend maps every code to the translation
 * key {@code deserialization.<CODE>}.
 */
public enum DeserializationErrorCode {

    /** The object's class (or a class it references) is not on the admin classpath. */
    CLASS_NOT_FOUND,
    /** The class exists but is incompatible (e.g. {@code serialVersionUID} mismatch). */
    INCOMPATIBLE_CLASS,
    /** No {@code DataSerializableFactory} is registered for the object's factory id. */
    DATA_SERIALIZABLE_FACTORY_MISSING,
    /** No {@code PortableFactory} is registered for the object's factory id. */
    PORTABLE_FACTORY_MISSING,
    /** The object was written by a custom serializer that is not registered in the admin client. */
    CUSTOM_SERIALIZER_MISSING,
    /** The Compact schema could not be obtained from the cluster. */
    COMPACT_SCHEMA_UNAVAILABLE,
    /** Any other deserialization failure. */
    DESERIALIZATION_ERROR,
    /** The factory id is served by the dynamic DTO registry, but no schema is registered for the class id. */
    DTO_SCHEMA_MISSING
}
