package io.github.noproblem2000.tsarevnacache.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the value inspector ({@code admin.inspector.*}).
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "admin.inspector")
public class InspectorProperties {

    /** Maximum length of the single-line value preview shown in tables. */
    @Min(16)
    @Max(10_000)
    private int previewLength = 160;

    /**
     * Package prefixes that may not be used as {@code CLASS} targets when writing values. Keeps the
     * Jackson binding limited to application DTOs.
     */
    private List<String> deniedClassPrefixes = new ArrayList<>(List.of(
            "java.",
            "javax.",
            "jdk.",
            "sun.",
            "com.sun.",
            "org.springframework.",
            "com.hazelcast.",
            "io.github.noproblem2000.tsarevnacache."));
}
