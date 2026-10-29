package io.github.noproblem2000.tsarevnacache.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Dynamic DTO schema registry ({@code admin.dto-schemas.*}).
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "admin.dto-schemas")
public class DtoSchemaProperties {

    /**
     * JSON file holding the DTO schemas and the factory mapping. Relative paths resolve against the
     * working directory (the distribution root when started with the bundled launchers).
     */
    @NotBlank
    private String file = "./config/dto-schemas.json";
}
