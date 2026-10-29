package io.github.noproblem2000.tsarevnacache.dto;

/**
 * Wire format of one DTO field, i.e. which {@link SerializationUtils} call reads and writes it.
 */
public enum DtoFieldType {

    NULLABLE_LONG("readNullableLong", "writeNullableLong"),
    NULLABLE_STRING("readNullableString", "writeNullableString"),
    NULLABLE_DATE("readNullableDate", "writeNullableDate"),
    /** Nested object written with {@code writeObject} (usually another DTO). */
    NULLABLE_OBJECT("readNullableObject", "writeNullableObject"),
    NULLABLE_BOOLEAN("readNullableBoolean", "writeNullableBoolean"),
    NULLABLE_INT("readNullableInt", "writeNullableInt"),
    NULLABLE_DOUBLE("readNullableDouble", "writeNullableDouble"),
    NULLABLE_BIG_DECIMAL("readNullableBigDecimal", "writeNullableBigDecimal"),
    /** List of objects written with {@code writeObject} (usually DTOs). */
    NULLABLE_LIST("readNullableList", "writeNullableList"),
    NULLABLE_LONG_LIST("readNullableLongList", "writeNullableLongList"),
    NULLABLE_INTEGER_LIST("readNullableIntegerList", "writeNullableIntegerList"),
    RAW_INT("readInt", "writeInt"),
    RAW_LONG("readLong", "writeLong"),
    RAW_BOOLEAN("readBoolean", "writeBoolean"),
    RAW_STRING("readString", "writeString"),
    RAW_BYTE_ARRAY("readByteArray", "writeByteArray");

    private final String readMethod;
    private final String writeMethod;

    DtoFieldType(String readMethod, String writeMethod) {
        this.readMethod = readMethod;
        this.writeMethod = writeMethod;
    }

    /** Method name used in {@code readData}: a {@code SerializationUtils} helper or an {@code ObjectDataInput} method. */
    public String readMethod() {
        return readMethod;
    }

    /** Method name used in {@code writeData}. */
    public String writeMethod() {
        return writeMethod;
    }

    public boolean isRaw() {
        return name().startsWith("RAW_");
    }

    /** Whether the field holds nested objects whose DTO class has to be known when writing JSON back. */
    public boolean hasNestedObjects() {
        return this == NULLABLE_OBJECT || this == NULLABLE_LIST;
    }
}
