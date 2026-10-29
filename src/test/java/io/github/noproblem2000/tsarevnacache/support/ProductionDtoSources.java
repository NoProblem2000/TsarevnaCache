package io.github.noproblem2000.tsarevnacache.support;

import java.util.Map;

/**
 * Java sources imitating the DTO project: its own {@code SerializationUtils}, a single
 * {@code DataSerializableFactory} with {@code FACTORY_ID = 1} and {@code IdentifiedDataSerializable}
 * DTOs written with the helpers. They are compiled for the embedded member only (see
 * {@link HiddenClasses}) and pasted as text into the schema parser by the tests, exactly as a
 * developer would copy them from the IDE.
 */
public final class ProductionDtoSources {

    public static final String SERIALIZATION_UTILS = """
            package prod.model;

            import com.hazelcast.nio.ObjectDataInput;
            import com.hazelcast.nio.ObjectDataOutput;
            import java.io.IOException;
            import java.math.BigDecimal;
            import java.util.ArrayList;
            import java.util.Date;
            import java.util.List;

            public final class SerializationUtils {
                private SerializationUtils() { }

                public static void writeNullableLong(ObjectDataOutput out, Long value) throws IOException {
                    out.writeBoolean(value != null);
                    if (value != null) { out.writeLong(value); }
                }
                public static Long readNullableLong(ObjectDataInput in) throws IOException {
                    return in.readBoolean() ? in.readLong() : null;
                }
                public static void writeNullableString(ObjectDataOutput out, String value) throws IOException {
                    out.writeBoolean(value != null);
                    if (value != null) { out.writeString(value); }
                }
                public static String readNullableString(ObjectDataInput in) throws IOException {
                    return in.readBoolean() ? in.readString() : null;
                }
                public static void writeNullableDate(ObjectDataOutput out, Date value) throws IOException {
                    out.writeBoolean(value != null);
                    if (value != null) { out.writeLong(value.getTime()); }
                }
                public static Date readNullableDate(ObjectDataInput in) throws IOException {
                    return in.readBoolean() ? new Date(in.readLong()) : null;
                }
                public static void writeNullableObject(ObjectDataOutput out, Object value) throws IOException {
                    out.writeBoolean(value != null);
                    if (value != null) { out.writeObject(value); }
                }
                public static <T> T readNullableObject(ObjectDataInput in) throws IOException {
                    return in.readBoolean() ? in.readObject() : null;
                }
                public static void writeNullableBoolean(ObjectDataOutput out, Boolean value) throws IOException {
                    out.writeByte(value == null ? -1 : (value ? 1 : 0));
                }
                public static Boolean readNullableBoolean(ObjectDataInput in) throws IOException {
                    byte value = in.readByte();
                    return value == -1 ? null : value == 1;
                }
                public static void writeNullableInt(ObjectDataOutput out, Integer value) throws IOException {
                    out.writeBoolean(value != null);
                    if (value != null) { out.writeInt(value); }
                }
                public static Integer readNullableInt(ObjectDataInput in) throws IOException {
                    return in.readBoolean() ? in.readInt() : null;
                }
                public static void writeNullableDouble(ObjectDataOutput out, Double value) throws IOException {
                    out.writeBoolean(value != null);
                    if (value != null) { out.writeDouble(value); }
                }
                public static Double readNullableDouble(ObjectDataInput in) throws IOException {
                    return in.readBoolean() ? in.readDouble() : null;
                }
                public static void writeNullableBigDecimal(ObjectDataOutput out, BigDecimal value) throws IOException {
                    out.writeBoolean(value != null);
                    if (value != null) { out.writeString(value.toString()); }
                }
                public static BigDecimal readNullableBigDecimal(ObjectDataInput in) throws IOException {
                    return in.readBoolean() ? new BigDecimal(in.readString()) : null;
                }
                public static void writeNullableList(ObjectDataOutput out, List<?> value) throws IOException {
                    if (value == null) { out.writeInt(-1); return; }
                    out.writeInt(value.size());
                    for (Object element : value) { out.writeObject(element); }
                }
                public static <T> List<T> readNullableList(ObjectDataInput in) throws IOException {
                    int size = in.readInt();
                    if (size == -1) { return null; }
                    List<T> list = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) { list.add(in.readObject()); }
                    return list;
                }
                public static void writeNullableLongList(ObjectDataOutput out, List<Long> value) throws IOException {
                    if (value == null) { out.writeInt(-1); return; }
                    out.writeInt(value.size());
                    for (Long element : value) { writeNullableLong(out, element); }
                }
                public static List<Long> readNullableLongList(ObjectDataInput in) throws IOException {
                    int size = in.readInt();
                    if (size == -1) { return null; }
                    List<Long> list = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) { list.add(readNullableLong(in)); }
                    return list;
                }
                public static void writeNullableIntegerList(ObjectDataOutput out, List<Integer> value) throws IOException {
                    if (value == null) { out.writeInt(-1); return; }
                    out.writeInt(value.size());
                    for (Integer element : value) { out.writeInt(element == null ? -1 : element); }
                }
                public static List<Integer> readNullableIntegerList(ObjectDataInput in) throws IOException {
                    int size = in.readInt();
                    if (size == -1) { return null; }
                    List<Integer> list = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) { int v = in.readInt(); list.add(v == -1 ? null : v); }
                    return list;
                }
            }
            """;

    public static final String FACTORY = """
            package prod.model;

            import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

            /** The single factory of the project. */
            public class DataSerializableFactory implements com.hazelcast.nio.serialization.DataSerializableFactory {

                public static final int FACTORY_ID = 1;

                public static final int SOMEDTO = 1;
                public static final int SOME2DTO = 2;
                public static final int UNKNOWNDTO = 3; // not registered in the admin service on purpose

                @Override
                public IdentifiedDataSerializable create(int typeId) {
                    return switch (typeId) {
                        case SOMEDTO -> new SomeDTO();
                        case SOME2DTO -> new Some2DTO();
                        case UNKNOWNDTO -> new UnknownDTO();
                        default -> null;
                    };
                }
            }
            """;

    public static final String SOME_DTO = """
            package prod.model;

            import com.hazelcast.nio.ObjectDataInput;
            import com.hazelcast.nio.ObjectDataOutput;
            import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
            import java.io.IOException;
            import java.math.BigDecimal;
            import java.util.Date;
            import java.util.List;

            /**
             * Order DTO; { braces } in comments must not confuse the parser.
             */
            public class SomeDTO implements IdentifiedDataSerializable {

                private Long id;
                private String name; // display name; readData(...) {
                private Date createdAt;
                private Some2DTO details;
                private Boolean active;
                private Integer count;
                private Double ratio;
                private BigDecimal amount;
                private List<Some2DTO> items;
                private List<Long> refs;
                private List<Integer> scores;
                private int version;
                private long stamp;
                private boolean flag;
                private String code;

                public SomeDTO() { }

                @Override
                public int getFactoryId() {
                    return DataSerializableFactory.FACTORY_ID;
                }

                @Override
                public int getClassId() {
                    return DataSerializableFactory.SOMEDTO;
                }

                @Override
                public void writeData(ObjectDataOutput out) throws IOException {
                    SerializationUtils.writeNullableLong(out, id);
                    SerializationUtils.writeNullableString(out, name);
                    SerializationUtils.writeNullableDate(out, createdAt);
                    SerializationUtils.writeNullableObject(out, details);
                    SerializationUtils.writeNullableBoolean(out, active);
                    SerializationUtils.writeNullableInt(out, count);
                    SerializationUtils.writeNullableDouble(out, ratio);
                    SerializationUtils.writeNullableBigDecimal(out, amount);
                    SerializationUtils.writeNullableList(out, items);
                    SerializationUtils.writeNullableLongList(out, refs);
                    SerializationUtils.writeNullableIntegerList(out, scores);
                    out.writeInt(version);
                    out.writeLong(stamp);
                    out.writeBoolean(flag);
                    out.writeString(code);
                }

                @Override
                public void readData(ObjectDataInput in) throws IOException {
                    this.id = SerializationUtils.readNullableLong(in);
                    this.name = SerializationUtils.readNullableString(in);
                    createdAt = SerializationUtils.readNullableDate(in);
                    this.details = SerializationUtils.readNullableObject(in);
                    this.active = SerializationUtils.readNullableBoolean(in);
                    this.count = SerializationUtils.readNullableInt(in);
                    this.ratio = SerializationUtils.readNullableDouble(in);
                    this.amount = SerializationUtils.readNullableBigDecimal(in);
                    this.items = SerializationUtils.<Some2DTO>readNullableList(in);
                    this.refs = SerializationUtils.readNullableLongList(in);
                    this.scores = SerializationUtils.readNullableIntegerList(in);
                    this.version = in.readInt();
                    this.stamp = in.readLong();
                    this.flag = in.readBoolean();
                    this.code = in.readString();
                }
            }
            """;

    public static final String SOME2_DTO = """
            package prod.model;

            import com.hazelcast.nio.ObjectDataInput;
            import com.hazelcast.nio.ObjectDataOutput;
            import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
            import java.io.IOException;

            public class Some2DTO implements IdentifiedDataSerializable {

                private String label;
                private Long value;

                public Some2DTO() { }

                public void setLabel(String label) { this.label = label; }
                public String getLabel() { return label; }
                public Long getValue() { return value; }

                @Override public int getFactoryId() { return DataSerializableFactory.FACTORY_ID; }
                @Override public int getClassId() { return DataSerializableFactory.SOME2DTO; }

                @Override
                public void writeData(ObjectDataOutput out) throws IOException {
                    SerializationUtils.writeNullableString(out, getLabel());
                    SerializationUtils.writeNullableLong(out, this.value);
                }

                @Override
                public void readData(ObjectDataInput in) throws IOException {
                    setLabel(SerializationUtils.readNullableString(in));
                    value = SerializationUtils.readNullableLong(in);
                }
            }
            """;

    public static final String UNKNOWN_DTO = """
            package prod.model;

            import com.hazelcast.nio.ObjectDataInput;
            import com.hazelcast.nio.ObjectDataOutput;
            import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
            import java.io.IOException;

            public class UnknownDTO implements IdentifiedDataSerializable {
                private String secret;
                public UnknownDTO() { }
                @Override public int getFactoryId() { return DataSerializableFactory.FACTORY_ID; }
                @Override public int getClassId() { return DataSerializableFactory.UNKNOWNDTO; }
                @Override public void writeData(ObjectDataOutput out) throws IOException { SerializationUtils.writeNullableString(out, secret); }
                @Override public void readData(ObjectDataInput in) throws IOException { secret = SerializationUtils.readNullableString(in); }
            }
            """;

    public static final Map<String, String> ALL = Map.of(
            "prod/model/SerializationUtils.java", SERIALIZATION_UTILS,
            "prod/model/DataSerializableFactory.java", FACTORY,
            "prod/model/SomeDTO.java", SOME_DTO,
            "prod/model/Some2DTO.java", SOME2_DTO,
            "prod/model/UnknownDTO.java", UNKNOWN_DTO);

    private ProductionDtoSources() {
    }
}
