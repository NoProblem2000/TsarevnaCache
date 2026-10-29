package io.github.noproblem2000.tsarevnacache.dto;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Binary helpers mirroring the {@code SerializationUtils} of the DTO project, call for call, so a
 * {@link DynamicDtoRecord} reads and writes exactly the bytes the real DTO classes produce.
 *
 * <p>Wire formats:
 * <ul>
 *   <li>{@code Long / String / Date / Object / Int / Double / BigDecimal}: {@code boolean} present,
 *       then the value ({@code long}, {@code writeString}, {@code long} epoch millis,
 *       {@code writeObject}, {@code int}, {@code double}, {@code writeString(toString())})</li>
 *   <li>{@code Boolean}: one {@code byte}; {@code -1} = null, {@code 1} = true, {@code 0} = false</li>
 *   <li>{@code List}: {@code int} size ({@code -1} = null), then {@code writeObject} per element</li>
 *   <li>{@code List<Long>}: {@code int} size ({@code -1} = null), then {@code writeNullableLong} per element</li>
 *   <li>{@code List<Integer>}: {@code int} size ({@code -1} = null), then one {@code int} per element where
 *       {@code -1} stands for a null element</li>
 *   <li>Raw: plain {@code ObjectDataOutput} calls ({@code int}, {@code long}, {@code boolean},
 *       {@code writeString}, {@code writeByteArray})</li>
 * </ul>
 */
public final class SerializationUtils {

    private static final byte NULL_BOOLEAN = -1;
    private static final int NULL_SIZE = -1;
    private static final int NULL_INTEGER_ELEMENT = -1;

    private SerializationUtils() {
    }

    // ------------------------------------------------------------------ Long

    public static void writeNullableLong(ObjectDataOutput out, Long value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeLong(value);
        }
    }

    public static Long readNullableLong(ObjectDataInput in) throws IOException {
        return in.readBoolean() ? in.readLong() : null;
    }

    // ------------------------------------------------------------------ String

    public static void writeNullableString(ObjectDataOutput out, String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeString(value);
        }
    }

    public static String readNullableString(ObjectDataInput in) throws IOException {
        return in.readBoolean() ? in.readString() : null;
    }

    // ------------------------------------------------------------------ Date

    public static void writeNullableDate(ObjectDataOutput out, Date value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeLong(value.getTime());
        }
    }

    public static Date readNullableDate(ObjectDataInput in) throws IOException {
        return in.readBoolean() ? new Date(in.readLong()) : null;
    }

    // ------------------------------------------------------------------ Object

    public static void writeNullableObject(ObjectDataOutput out, Object value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeObject(value);
        }
    }

    public static <T> T readNullableObject(ObjectDataInput in) throws IOException {
        return in.readBoolean() ? in.readObject() : null;
    }

    // ------------------------------------------------------------------ Boolean

    public static void writeNullableBoolean(ObjectDataOutput out, Boolean value) throws IOException {
        out.writeByte(value == null ? NULL_BOOLEAN : (value ? 1 : 0));
    }

    public static Boolean readNullableBoolean(ObjectDataInput in) throws IOException {
        byte value = in.readByte();
        return value == NULL_BOOLEAN ? null : value != 0;
    }

    // ------------------------------------------------------------------ Integer

    public static void writeNullableInt(ObjectDataOutput out, Integer value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeInt(value);
        }
    }

    public static Integer readNullableInt(ObjectDataInput in) throws IOException {
        return in.readBoolean() ? in.readInt() : null;
    }

    // ------------------------------------------------------------------ Double

    public static void writeNullableDouble(ObjectDataOutput out, Double value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeDouble(value);
        }
    }

    public static Double readNullableDouble(ObjectDataInput in) throws IOException {
        return in.readBoolean() ? in.readDouble() : null;
    }

    // ------------------------------------------------------------------ BigDecimal

    public static void writeNullableBigDecimal(ObjectDataOutput out, BigDecimal value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeString(value.toString());
        }
    }

    public static BigDecimal readNullableBigDecimal(ObjectDataInput in) throws IOException {
        return in.readBoolean() ? new BigDecimal(in.readString()) : null;
    }

    // ------------------------------------------------------------------ List<Object>

    public static void writeNullableList(ObjectDataOutput out, List<?> value) throws IOException {
        if (value == null) {
            out.writeInt(NULL_SIZE);
            return;
        }
        out.writeInt(value.size());
        for (Object element : value) {
            out.writeObject(element);
        }
    }

    public static <T> List<T> readNullableList(ObjectDataInput in) throws IOException {
        int size = in.readInt();
        if (size == NULL_SIZE) {
            return null;
        }
        List<T> result = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            result.add(in.readObject());
        }
        return result;
    }

    // ------------------------------------------------------------------ List<Long>

    public static void writeNullableLongList(ObjectDataOutput out, List<Long> value) throws IOException {
        if (value == null) {
            out.writeInt(NULL_SIZE);
            return;
        }
        out.writeInt(value.size());
        for (Long element : value) {
            writeNullableLong(out, element);
        }
    }

    public static List<Long> readNullableLongList(ObjectDataInput in) throws IOException {
        int size = in.readInt();
        if (size == NULL_SIZE) {
            return null;
        }
        List<Long> result = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            result.add(readNullableLong(in));
        }
        return result;
    }

    // ------------------------------------------------------------------ List<Integer>

    public static void writeNullableIntegerList(ObjectDataOutput out, List<Integer> value) throws IOException {
        if (value == null) {
            out.writeInt(NULL_SIZE);
            return;
        }
        out.writeInt(value.size());
        for (Integer element : value) {
            out.writeInt(element == null ? NULL_INTEGER_ELEMENT : element);
        }
    }

    public static List<Integer> readNullableIntegerList(ObjectDataInput in) throws IOException {
        int size = in.readInt();
        if (size == NULL_SIZE) {
            return null;
        }
        List<Integer> result = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            int element = in.readInt();
            result.add(element == NULL_INTEGER_ELEMENT ? null : element);
        }
        return result;
    }

    // ------------------------------------------------------------------ raw ObjectDataInput/Output calls

    public static void writeRawInt(ObjectDataOutput out, int value) throws IOException {
        out.writeInt(value);
    }

    public static int readRawInt(ObjectDataInput in) throws IOException {
        return in.readInt();
    }

    public static void writeRawLong(ObjectDataOutput out, long value) throws IOException {
        out.writeLong(value);
    }

    public static long readRawLong(ObjectDataInput in) throws IOException {
        return in.readLong();
    }

    public static void writeRawBoolean(ObjectDataOutput out, boolean value) throws IOException {
        out.writeBoolean(value);
    }

    public static boolean readRawBoolean(ObjectDataInput in) throws IOException {
        return in.readBoolean();
    }

    public static void writeRawString(ObjectDataOutput out, String value) throws IOException {
        out.writeString(value);
    }

    public static String readRawString(ObjectDataInput in) throws IOException {
        return in.readString();
    }

    public static void writeRawByteArray(ObjectDataOutput out, byte[] value) throws IOException {
        out.writeByteArray(value);
    }

    public static byte[] readRawByteArray(ObjectDataInput in) throws IOException {
        return in.readByteArray();
    }
}
