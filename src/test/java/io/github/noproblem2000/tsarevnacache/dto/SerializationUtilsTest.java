package io.github.noproblem2000.tsarevnacache.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.hazelcast.internal.serialization.InternalSerializationService;
import com.hazelcast.internal.serialization.impl.DefaultSerializationServiceBuilder;
import com.hazelcast.internal.nio.BufferObjectDataInput;
import com.hazelcast.internal.nio.BufferObjectDataOutput;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Wire layout of the helpers; it must never change (it is the format of the stored DTOs). */
class SerializationUtilsTest {

    private final InternalSerializationService serialization = new DefaultSerializationServiceBuilder().build();

    @AfterEach
    void destroy() {
        serialization.dispose();
    }

    @FunctionalInterface
    private interface Writer {
        void write(BufferObjectDataOutput out) throws IOException;
    }

    private String hex(Writer writer) throws IOException {
        BufferObjectDataOutput out = serialization.createObjectDataOutput();
        writer.write(out);
        return HexFormat.of().formatHex(out.toByteArray());
    }

    private BufferObjectDataInput input(Writer writer) throws IOException {
        BufferObjectDataOutput out = serialization.createObjectDataOutput();
        writer.write(out);
        return serialization.createObjectDataInput(out.toByteArray());
    }

    @Test
    void nullableScalarsUseAPresenceFlag() throws IOException {
        assertThat(hex(out -> SerializationUtils.writeNullableLong(out, null))).isEqualTo("00");
        assertThat(hex(out -> SerializationUtils.writeNullableLong(out, 1L))).isEqualTo("010000000000000001");
        assertThat(hex(out -> SerializationUtils.writeNullableInt(out, 2))).isEqualTo("0100000002");
        assertThat(hex(out -> SerializationUtils.writeNullableDate(out, new Date(3)))).isEqualTo("010000000000000003");
        assertThat(hex(out -> SerializationUtils.writeNullableString(out, "A"))).isEqualTo("010000000141");
        assertThat(hex(out -> SerializationUtils.writeNullableBigDecimal(out, new BigDecimal("1E+3"))))
                .isEqualTo("0100000004" + HexFormat.of().formatHex("1E+3".getBytes()));
        assertThat(hex(out -> SerializationUtils.writeNullableDouble(out, null))).isEqualTo("00");
    }

    @Test
    void nullableBooleanIsOneByte() throws IOException {
        assertThat(hex(out -> SerializationUtils.writeNullableBoolean(out, null))).isEqualTo("ff");
        assertThat(hex(out -> SerializationUtils.writeNullableBoolean(out, true))).isEqualTo("01");
        assertThat(hex(out -> SerializationUtils.writeNullableBoolean(out, false))).isEqualTo("00");
        assertThat(SerializationUtils.readNullableBoolean(input(out -> out.writeByte(-1)))).isNull();
        assertThat(SerializationUtils.readNullableBoolean(input(out -> out.writeByte(1)))).isTrue();
    }

    @Test
    void listsUseMinusOneForNull() throws IOException {
        assertThat(hex(out -> SerializationUtils.writeNullableList(out, null))).isEqualTo("ffffffff");
        assertThat(hex(out -> SerializationUtils.writeNullableLongList(out, Arrays.asList(5L, null))))
                .isEqualTo("00000002" + "010000000000000005" + "00");
        assertThat(hex(out -> SerializationUtils.writeNullableIntegerList(out, Arrays.asList(7, null))))
                .isEqualTo("00000002" + "00000007" + "ffffffff");
        assertThat(SerializationUtils.readNullableIntegerList(
                input(out -> SerializationUtils.writeNullableIntegerList(out, Arrays.asList(7, null)))))
                .containsExactly(7, null);
        assertThat(SerializationUtils.readNullableLongList(input(out -> out.writeInt(-1)))).isNull();
    }

    @Test
    void valuesRoundTrip() throws IOException {
        Date date = new Date(1_700_000_000_123L);
        BufferObjectDataInput in = input(out -> {
            SerializationUtils.writeNullableLong(out, Long.MIN_VALUE);
            SerializationUtils.writeNullableString(out, "Привет");
            SerializationUtils.writeNullableDate(out, date);
            SerializationUtils.writeNullableObject(out, "nested");
            SerializationUtils.writeNullableObject(out, null);
            SerializationUtils.writeNullableInt(out, null);
            SerializationUtils.writeNullableDouble(out, 0.1);
            SerializationUtils.writeNullableBigDecimal(out, new BigDecimal("12.50"));
            SerializationUtils.writeNullableList(out, List.of("a", 1L));
            SerializationUtils.writeRawInt(out, -5);
            SerializationUtils.writeRawLong(out, 6L);
            SerializationUtils.writeRawBoolean(out, true);
            SerializationUtils.writeRawString(out, null);
            SerializationUtils.writeRawByteArray(out, new byte[] {1, 2});
        });
        assertThat(SerializationUtils.readNullableLong(in)).isEqualTo(Long.MIN_VALUE);
        assertThat(SerializationUtils.readNullableString(in)).isEqualTo("Привет");
        assertThat(SerializationUtils.readNullableDate(in)).isEqualTo(date);
        assertThat(SerializationUtils.<String>readNullableObject(in)).isEqualTo("nested");
        assertThat(SerializationUtils.<Object>readNullableObject(in)).isNull();
        assertThat(SerializationUtils.readNullableInt(in)).isNull();
        assertThat(SerializationUtils.readNullableDouble(in)).isEqualTo(0.1);
        assertThat(SerializationUtils.readNullableBigDecimal(in)).isEqualTo(new BigDecimal("12.50"));
        assertThat(SerializationUtils.readNullableList(in)).containsExactly("a", 1L);
        assertThat(SerializationUtils.readRawInt(in)).isEqualTo(-5);
        assertThat(SerializationUtils.readRawLong(in)).isEqualTo(6L);
        assertThat(SerializationUtils.readRawBoolean(in)).isTrue();
        assertThat(SerializationUtils.readRawString(in)).isNull();
        assertThat(SerializationUtils.readRawByteArray(in)).containsExactly(1, 2);
    }
}
