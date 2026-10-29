package io.github.noproblem2000.tsarevnacache.dto;

import com.hazelcast.nio.serialization.HazelcastSerializationException;

/**
 * Thrown by {@link DynamicDataSerializableFactory#create(int)} for a class id that has neither a
 * schema nor a class in the delegate factory. It carries the ids of exactly the object that could
 * not be created, which may be a DTO nested deep inside the stored value; the inspector reads them
 * from the cause chain instead of the (registered) top-level header.
 */
public class DtoSchemaMissingException extends HazelcastSerializationException {

    private final int factoryId;
    private final int classId;
    private final String className;

    /**
     * @param className class name known from the factory mapping, or {@code null}
     */
    public DtoSchemaMissingException(int factoryId, int classId, String className) {
        super("DTO " + label(factoryId, classId, className) + " is not registered: no schema and no class in the factory");
        this.factoryId = factoryId;
        this.classId = classId;
        this.className = className;
    }

    public int factoryId() {
        return factoryId;
    }

    public int classId() {
        return classId;
    }

    /** Class name from the factory mapping, may be {@code null}. */
    public String className() {
        return className;
    }

    static String label(int factoryId, int classId, String className) {
        return (className == null ? "" : className + " ") + "(factoryId=" + factoryId + ", classId=" + classId + ")";
    }
}
