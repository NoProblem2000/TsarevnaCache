package testdto;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;

/** DTO without getters, setters or {@code Serializable}; stored with zero-config Compact. */
public class PlainDto {

    private String name;
    private int age;
    private String[] tags;
    private LocalDate born;

    public PlainDto() {
    }

    public PlainDto(String name, int age, String[] tags, LocalDate born) {
        this.name = name;
        this.age = age;
        this.tags = tags;
        this.born = born;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PlainDto other && age == other.age && Objects.equals(name, other.name)
                && Arrays.equals(tags, other.tags) && Objects.equals(born, other.born);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age, Arrays.hashCode(tags), born);
    }
}
