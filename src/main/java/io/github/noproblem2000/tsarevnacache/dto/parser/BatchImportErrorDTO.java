package io.github.noproblem2000.tsarevnacache.dto.parser;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Problem in a file that is a valid DTO: a field that could not be recognized (the schema is still
 * imported and should be reviewed) or a DTO that could not be imported at all.
 *
 * @param fileName  name of the file
 * @param className DTO class name, when known
 * @param message   what went wrong
 * @param imported  {@code true} when the schema was imported nevertheless
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchImportErrorDTO(String fileName, String className, String message, boolean imported) {
}
