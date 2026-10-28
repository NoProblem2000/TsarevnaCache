package testdto;

import java.io.Serializable;

/** DTO with final fields and no no-arg constructor (requires allocation without a constructor). */
public class NoDefaultConstructorDto implements Serializable {

    private final String code;
    private final long count;

    public NoDefaultConstructorDto(String code, long count, boolean ignored) {
        this.code = code;
        this.count = count;
    }

    public String code() {
        return code;
    }

    public long count() {
        return count;
    }
}
