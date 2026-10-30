package io.github.noproblem2000.tsarevnacache.dto.parser;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A file of a batch import that was skipped on purpose (not an error).
 *
 * @param fileName  name of the file
 * @param className primary type declared in the file, when one was found
 * @param reason    {@value JavaDtoSourceParserService#NOT_A_DTO}, {@value JavaDtoSourceParserService#NOT_REGISTERED}
 *                  or why the file could not be read as Java source
 * @param detail    what exactly made the decision, e.g. {@code enum} or {@code no readData/writeData}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IgnoredFileDTO(String fileName, String className, String reason, String detail) {
}
