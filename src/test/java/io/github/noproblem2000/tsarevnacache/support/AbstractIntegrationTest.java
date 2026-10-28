package io.github.noproblem2000.tsarevnacache.support;

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

    @DynamicPropertySource
    static void clusterProperties(DynamicPropertyRegistry registry) {
        EmbeddedCluster cluster = EmbeddedCluster.get();
        registry.add("admin.hazelcast.cluster-name", cluster::clusterName);
        registry.add("admin.hazelcast.members", cluster::address);
        registry.add("admin.hazelcast.instance-name", () -> "");
        registry.add("admin.hazelcast.async-start", () -> "false");
        registry.add("admin.security.username", () -> ADMIN_USER);
        registry.add("admin.security.password", () -> ADMIN_PASSWORD);
    }

    protected EmbeddedCluster cluster() {
        return EmbeddedCluster.get();
    }
}
