package io.github.noproblem2000.tsarevnacache.dto.parser;

import java.util.List;
import java.util.Map;

/**
 * What was recognized in a pasted {@code DataSerializableFactory.java}.
 *
 * @param factoryId value of {@code FACTORY_ID}
 * @param constants class id constants, e.g. {@code SOMEDTO -> 1}
 * @param classes   class names created by the factory, e.g. {@code SomeDTO -> 1}
 * @param warnings  things that could not be mapped and should be checked
 */
public record FactoryParseResult(
        int factoryId,
        Map<String, Integer> constants,
        Map<String, Integer> classes,
        List<String> warnings) {
}
