package io.github.noproblem2000.tsarevnacache.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the full application against the shared {@link EmbeddedCluster}.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    protected static final String ADMIN_USER = "admin";
    protected static final String ADMIN_PASSWORD = "test-secret";

    /** DTO schema registry of the test application (never the developer's {@code ./config}). */
    protected static final Path DTO_SCHEMAS_FILE = tempDirectory().resolve("dto-schemas.json");

    /** Healthcheck ignore list of the test application. */
    protected static final Path HEALTHCHECK_IGNORES_FILE = DTO_SCHEMAS_FILE.resolveSibling("healthcheck-ignores.json");

    private static Path tempDirectory() {
        try {
            return Files.createTempDirectory("tsarevna-schemas");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void clusterProperties(DynamicPropertyRegistry registry) {
        EmbeddedCluster cluster = EmbeddedCluster.get();
        registry.add("admin.hazelcast.cluster-name", cluster::clusterName);
        registry.add("admin.hazelcast.members", cluster::address);
        registry.add("admin.hazelcast.instance-name", () -> "");
        registry.add("admin.hazelcast.async-start", () -> "false");
        registry.add("admin.security.username", () -> ADMIN_USER);
        registry.add("admin.security.password", () -> ADMIN_PASSWORD);
        registry.add("admin.dto-schemas.file", () -> DTO_SCHEMAS_FILE.toString());
        registry.add("admin.healthcheck.ignores-file", () -> HEALTHCHECK_IGNORES_FILE.toString());
        // runs are triggered by the tests themselves
        registry.add("admin.healthcheck.enabled", () -> "false");
    }

    protected EmbeddedCluster cluster() {
        return EmbeddedCluster.get();
    }
}
