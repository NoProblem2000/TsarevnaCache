package io.github.noproblem2000.tsarevnacache.dto;

import com.hazelcast.nio.serialization.HazelcastSerializationException;

/**
 * Failure of {@link DynamicDtoRecord#readData} at one field: names the DTO (class name and ids),
 * the field position, name and wire type, and keeps the original error as the cause.
 *
 * <p>When a nested DTO fails, every enclosing record adds its own {@code DtoFieldReadException}, so
 * the cause chain is the path from the stored (root) object down to the failing field; the
 * innermost one is where reading actually broke. A {@link DtoSchemaMissingException} at the end of
 * the chain means the nested class is not registered rather than that the field layout is wrong.
 */
public class DtoFieldReadException extends HazelcastSerializationException {

    private final int factoryId;
    private final int classId;
    private final String className;
    private final int fieldOrder;
    private final String fieldName;
    private final DtoFieldType fieldType;
    private final String originalMessage;

    public DtoFieldReadException(DtoClassSchema schema, DtoFieldSchema field, Throwable cause) {
        super(message(schema, field, describe(cause)), cause);
        this.factoryId = schema.factoryId();
        this.classId = schema.classId();
        this.className = schema.className();
        this.fieldOrder = field.order();
        this.fieldName = field.fieldName();
        this.fieldType = field.fieldType();
        this.originalMessage = describe(cause);
    }

    public int factoryId() {
        return factoryId;
    }

    public int classId() {
        return classId;
    }

    public String className() {
        return className;
    }

    public int fieldOrder() {
        return fieldOrder;
    }

    public String fieldName() {
        return fieldName;
    }

    public DtoFieldType fieldType() {
        return fieldType;
    }

    /** Message of the error that broke the read (for a nested failure: the nested message). */
    public String originalMessage() {
        return originalMessage;
    }

    /** {@code SomeDTO.field3} */
    public String fieldPath() {
        return className + "." + fieldName;
    }

    private static String message(DtoClassSchema schema, DtoFieldSchema field, String original) {
        return "Failed in " + schema.className() + " (classId=" + schema.classId() + ") at field #" + field.order()
                + " '" + field.fieldName() + "' (type=" + field.fieldType() + "): " + original;
    }

    private static String describe(Throwable error) {
        if (error instanceof DtoFieldReadException || error instanceof DtoSchemaMissingException) {
            return error.getMessage();
        }
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            if (root.getCause() instanceof DtoFieldReadException || root.getCause() instanceof DtoSchemaMissingException) {
                return root.getCause().getMessage();
            }
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }
}
