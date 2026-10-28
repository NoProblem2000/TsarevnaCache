package io.github.noproblem2000.tsarevnacache.config;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.client.config.ConnectionRetryConfig;
import com.hazelcast.client.properties.ClientProperty;
import com.hazelcast.config.CompactSerializationConfig;
import com.hazelcast.config.ListenerConfig;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleEvent;
import com.hazelcast.core.LifecycleListener;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import io.github.noproblem2000.tsarevnacache.config.properties.HazelcastClientProperties;
import io.github.noproblem2000.tsarevnacache.config.properties.HazelcastClientProperties.CustomSerializer;
import io.github.noproblem2000.tsarevnacache.config.properties.HazelcastClientProperties.Retry;
import io.github.noproblem2000.tsarevnacache.config.properties.HazelcastClientProperties.Serialization;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Creates the Hazelcast client used by every service of the admin application.
 *
 * <p>Highlights:
 * <ul>
 *   <li>Compact serialization works without registration: data whose class is not on the classpath
 *       is returned as {@code GenericRecord}, which the value inspector renders field by field.</li>
 *   <li>Optional user artefacts (Compact classes/serializers, DataSerializable and Portable
 *       factories, custom serializers) are resolved through the shared {@link DtoClassLoader}.</li>
 *   <li>Asynchronous start and reconnect let the application run while the cluster is down.</li>
 * </ul>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class HazelcastClientConfig {

    /**
     * Class loader for user DTOs, closed on shutdown after the client (which depends on it).
     */
    @Bean(destroyMethod = "close")
    public DtoClassLoader dtoClassLoader(HazelcastClientProperties properties) {
        return DtoClassLoader.create(
                properties.getSerialization().getDtoClasspath(),
                HazelcastClientConfig.class.getClassLoader());
    }

    @Bean
    public ClientConfig hazelcastClientConfiguration(HazelcastClientProperties properties,
                                                     DtoClassLoader dtoClassLoader) {
        ClientConfig config = new ClientConfig();
        config.setClusterName(properties.getClusterName());
        config.setClassLoader(dtoClassLoader);
        if (StringUtils.hasText(properties.getInstanceName())) {
            config.setInstanceName(properties.getInstanceName());
        }
        properties.getLabels().stream().filter(StringUtils::hasText).forEach(config::addLabel);

        config.setProperty("hazelcast.logging.type", "slf4j");
        config.setProperty(ClientProperty.INVOCATION_TIMEOUT_SECONDS.getName(),
                String.valueOf(Math.max(1, properties.getInvocationTimeout().toSeconds())));
        config.setProperty(ClientProperty.HEARTBEAT_INTERVAL.getName(),
                String.valueOf(properties.getHeartbeatInterval().toMillis()));
        config.setProperty(ClientProperty.HEARTBEAT_TIMEOUT.getName(),
                String.valueOf(properties.getHeartbeatTimeout().toMillis()));

        configureNetwork(config.getNetworkConfig(), properties);
        configureConnectionStrategy(config.getConnectionStrategyConfig(), properties);
        configureSerialization(config.getSerializationConfig(), properties.getSerialization(), dtoClassLoader);

        config.addListenerConfig(new ListenerConfig(new ConnectionStateLogger(properties.getClusterName())));
        return config;
    }

    /**
     * The Hazelcast client. With {@code async-start} enabled this returns immediately and the
     * connection is established in the background.
     */
    @Bean(destroyMethod = "shutdown")
    public HazelcastInstance hazelcastClient(ClientConfig hazelcastClientConfiguration) {
        log.info("Starting Hazelcast client for cluster '{}' (members: {})",
                hazelcastClientConfiguration.getClusterName(),
                hazelcastClientConfiguration.getNetworkConfig().getAddresses());
        return HazelcastClient.newHazelcastClient(hazelcastClientConfiguration);
    }

    @SuppressWarnings("deprecation")
    private static void configureNetwork(ClientNetworkConfig network, HazelcastClientProperties properties) {
        properties.getMembers().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(network::addAddress);
        network.setSmartRouting(properties.isSmartRouting());
        network.setConnectionTimeout(toIntMillis(properties.getConnectionTimeout()));
        network.setRedoOperation(false);
    }

    private static void configureConnectionStrategy(ClientConnectionStrategyConfig strategy,
                                                    HazelcastClientProperties properties) {
        strategy.setAsyncStart(properties.isAsyncStart());
        strategy.setReconnectMode(properties.getReconnectMode());

        Retry retry = properties.getRetry();
        ConnectionRetryConfig retryConfig = strategy.getConnectionRetryConfig();
        retryConfig.setInitialBackoffMillis(toIntMillis(retry.getInitialBackoff()));
        retryConfig.setMaxBackoffMillis(toIntMillis(retry.getMaxBackoff()));
        retryConfig.setMultiplier(retry.getMultiplier());
        retryConfig.setJitter(retry.getJitter());
        if (retry.getClusterConnectTimeout() != null) {
            retryConfig.setClusterConnectTimeoutMillis(retry.getClusterConnectTimeout().toMillis());
        }
    }

    private static void configureSerialization(SerializationConfig serialization,
                                               Serialization properties,
                                               DtoClassLoader classLoader) {
        serialization.setPortableVersion(properties.getPortableVersion());

        CompactSerializationConfig compact = serialization.getCompactSerializationConfig();
        for (String className : properties.getCompactClasses()) {
            if (StringUtils.hasText(className)) {
                compact.addClass(loadClass(classLoader, className));
                log.info("Registered Compact class {}", className);
            }
        }
        for (String className : properties.getCompactSerializers()) {
            if (StringUtils.hasText(className)) {
                compact.addSerializer(instantiate(classLoader, className, CompactSerializer.class));
                log.info("Registered Compact serializer {}", className);
            }
        }

        properties.getDataSerializableFactories().forEach((factoryId, className) -> {
            serialization.addDataSerializableFactoryClass(factoryId, className.trim());
            log.info("Registered DataSerializableFactory {} -> {}", factoryId, className);
        });
        properties.getPortableFactories().forEach((factoryId, className) -> {
            serialization.addPortableFactoryClass(factoryId, className.trim());
            log.info("Registered PortableFactory {} -> {}", factoryId, className);
        });

        for (CustomSerializer custom : properties.getCustomSerializers()) {
            serialization.addSerializerConfig(new SerializerConfig()
                    .setTypeClassName(custom.getTypeClass().trim())
                    .setClassName(custom.getSerializerClass().trim()));
            log.info("Registered custom serializer {} for {}", custom.getSerializerClass(), custom.getTypeClass());
        }
    }

    private static Class<?> loadClass(DtoClassLoader classLoader, String className) {
        try {
            return classLoader.load(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Serialization class not found: " + className
                    + " (check admin.hazelcast.serialization.dto-classpath)", e);
        }
    }

    private static <T> T instantiate(DtoClassLoader classLoader, String className, Class<T> expectedType) {
        Class<?> type = loadClass(classLoader, className);
        if (!expectedType.isAssignableFrom(type)) {
            throw new IllegalStateException(className + " does not implement " + expectedType.getName());
        }
        try {
            return expectedType.cast(type.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate " + className
                    + " (a public no-arg constructor is required)", e);
        }
    }

    private static int toIntMillis(Duration duration) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, duration.toMillis()));
    }

    /**
     * Logs client lifecycle transitions so connection problems are visible in the service log.
     */
    private record ConnectionStateLogger(String clusterName) implements LifecycleListener {

        @Override
        public void stateChanged(LifecycleEvent event) {
            switch (event.getState()) {
                case CLIENT_CONNECTED -> log.info("Connected to Hazelcast cluster '{}'", clusterName);
                case CLIENT_DISCONNECTED -> log.warn("Disconnected from Hazelcast cluster '{}', reconnecting...",
                        clusterName);
                case SHUTDOWN -> log.info("Hazelcast client shut down");
                default -> log.debug("Hazelcast client state: {}", event.getState());
            }
        }
    }
}
