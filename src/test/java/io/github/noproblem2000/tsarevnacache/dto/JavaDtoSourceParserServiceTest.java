package io.github.noproblem2000.tsarevnacache.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.config.properties.DtoSchemaProperties;
import io.github.noproblem2000.tsarevnacache.dto.parser.DtoParseResult;
import io.github.noproblem2000.tsarevnacache.dto.parser.FactoryParseResult;
import io.github.noproblem2000.tsarevnacache.dto.parser.JavaDtoSourceParserService;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaDtoSourceParserServiceTest {

    @TempDir
    Path dir;

    private DtoSchemaRegistryService registry;
    private JavaDtoSourceParserService parser;

    @BeforeEach
    void setUp() {
        DtoSchemaProperties properties = new DtoSchemaProperties();
        properties.setFile(dir.resolve("dto-schemas.json").toString());
        registry = new DtoSchemaRegistryService(properties, new ObjectMapper());
        parser = new JavaDtoSourceParserService(registry);
    }

    @Test
    void classicSwitchAndInterfaceConstantsAreParsed() {
        FactoryParseResult result = parser.parseFactorySource("""
                public interface Ids {
                    int FACTORY_ID = 5;
                    int ORDER = 10;
                    int LINE = 11;
                }
                class Factory implements DataSerializableFactory {
                    public IdentifiedDataSerializable create(int typeId) {
                        switch (typeId) {
                            case Ids.ORDER: return new Order();
                            case Ids.LINE:
                                return new OrderLine();
                            default: return null;
                        }
                    }
                }""");
        assertThat(result.factoryId()).isEqualTo(5);
        assertThat(result.constants()).containsEntry("ORDER", 10).containsEntry("LINE", 11);
        assertThat(result.classes()).containsExactly(Map.entry("Order", 10), Map.entry("OrderLine", 11));
    }

    @Test
    void constructorMapsAreParsed() {
        FactoryParseResult result = parser.parseFactorySource("""
                class Factory {
                    public static final int FACTORY_ID = 1;
                    static final int A = 1;
                    static final int B = 2;
                    private final Map<Integer, Supplier<IdentifiedDataSerializable>> c = new HashMap<>();
                    { c.put(A, ADto::new); c.put(B, () -> new BDto()); }
                }""");
        assertThat(result.classes()).containsEntry("ADto", 1).containsEntry("BDto", 2);
    }

    @Test
    void writeDataIsUsedWhenReadDataIsMissingAndCastsAreAccepted() {
        registry.updateFactory(1, Map.of("PERSON", 4), Map.of("Person", 4));
        DtoParseResult result = parser.parseDtoClassSource("""
                public class Person implements IdentifiedDataSerializable {
                    private Long id;
                    private String nick;
                    private byte[] photo;
                    public int getClassId() { return PERSON; }
                    public void writeData(ObjectDataOutput out) throws IOException {
                        SerializationUtils.writeNullableLong(out, id);
                        out.writeUTF(nick);
                        out.writeByteArray(photo);
                    }
                    public void readData(ObjectDataInput in) { super.readData(in); }
                }""");
        assertThat(result.schema().classId()).isEqualTo(4);
        assertThat(result.schema().fields()).extracting(DtoFieldSchema::fieldType)
                .containsExactly(DtoFieldType.NULLABLE_LONG, DtoFieldType.RAW_STRING, DtoFieldType.RAW_BYTE_ARRAY);
        assertThat(result.source()).isEqualTo("writeData");
    }

    @Test
    void setterTargetsAndMismatchesAreReported() {
        DtoParseResult result = parser.parseDtoClassSource("""
                public class Item implements IdentifiedDataSerializable {
                    private Integer qty;
                    public int getFactoryId() { return 1; }
                    public int getClassId() { return 7; }
                    public void writeData(ObjectDataOutput out) throws IOException {
                        SerializationUtils.writeNullableLong(out, (long) qty);
                    }
                    public void readData(ObjectDataInput in) throws IOException {
                        setQty((Integer) SerializationUtils.readNullableInt(in));
                        if (qty == null) { qty = 0; }
                    }
                }""");
        assertThat(result.schema().fields()).singleElement().satisfies(field -> {
            assertThat(field.fieldName()).isEqualTo("qty");
            assertThat(field.fieldType()).isEqualTo(DtoFieldType.NULLABLE_INT);
        });
        assertThat(result.warnings()).anyMatch(w -> w.contains("writeData")).anyMatch(w -> w.contains("if"));
    }

    @Test
    void classIdIsFoundWithoutRegardToCaseAndNeverDefaulted() {
        FactoryParseResult factory = parser.parseFactorySource("""
                public class DataSerializableFactory implements com.hazelcast.nio.serialization.DataSerializableFactory {
                    public static final int FACTORY_ID = 1;
                    public static final int SOMEDTO = 1;
                    public static final int SOMEDTO2 = 2;
                    public static final int OTHER_DTO = 5;
                    public IdentifiedDataSerializable create(int typeId) {
                        return switch (typeId) {
                            case SOMEDTO -> new SomeDTO();
                            case SOMEDTO2 -> new Some2DTO();
                            case OTHER_DTO -> new OtherDto();
                            default -> null;
                        };
                    }
                }""");
        registry.updateFactory(factory.factoryId(), factory.constants(), factory.classes());

        // the constant spelled like the class name
        DtoParseResult byConstant = parser.parseDtoClassSource("""
                @Description("This class is stored in Hazelcast")
                public class SomeDTO implements IdentifiedDataSerializable {
                    private Some2DTO field3;
                    public int getClassId() { return DataSerializableFactory.SomeDTO; }
                    public void readData(ObjectDataInput in) throws IOException {
                        field3 = SerializationUtils.readNullableObject(in);
                    }
                }""");
        assertThat(byConstant.schema().className()).isEqualTo("SomeDTO");
        assertThat(byConstant.schema().classId()).isEqualTo(1);
        assertThat(byConstant.schema().fields().get(0).nestedClassName()).isEqualTo("Some2DTO");
        assertThat(byConstant.schema().fields().get(0).nestedClassId()).isEqualTo(2);

        // unknown constant: the class name from the factory switch (Some2DTO -> SOMEDTO2)
        DtoParseResult byClassName = parser.parseDtoClassSource("""
                public class Some2DTO implements IdentifiedDataSerializable {
                    private String label;
                    public int getClassId() { return Ids.SECOND; }
                    public void readData(ObjectDataInput in) throws IOException { label = in.readString(); }
                }""");
        assertThat(byClassName.schema().classId()).isEqualTo(2);

        // neither: no default id, the UI has to ask
        DtoParseResult unknown = parser.parseDtoClassSource("""
                public class Fifth implements IdentifiedDataSerializable {
                    private String label;
                    public int getClassId() { return Ids.FIFTH; }
                    public void readData(ObjectDataInput in) throws IOException { label = in.readString(); }
                }""");
        assertThat(unknown.schema().classId()).isNull();
        assertThat(unknown.complete()).isFalse();
        assertThat(unknown.warnings()).anyMatch(w -> w.contains("classId could not be resolved"));
    }

    @Test
    void unknownSourceIsRejected() {
        assertThatThrownBy(() -> parser.parseDtoClassSource("hello"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DTO_SOURCE_NOT_RECOGNIZED));
    }
}
