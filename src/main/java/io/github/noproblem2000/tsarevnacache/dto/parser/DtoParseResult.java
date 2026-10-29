package io.github.noproblem2000.tsarevnacache.dto.parser;

import io.github.noproblem2000.tsarevnacache.dto.DtoClassSchema;
import java.util.List;

/**
 * Schema recognized in a pasted DTO class; a preview that is not saved yet.
 *
 * @param schema   recognized schema ({@code classId} may be {@code null} when it could not be resolved)
 * @param source   which method the field order was taken from ({@code readData} or {@code writeData})
 * @param complete {@code false} when parts of the class could not be understood (see warnings)
 * @param warnings things to check before saving
 */
public record DtoParseResult(
        DtoClassSchema schema,
        String source,
        boolean complete,
        List<String> warnings) {
}
