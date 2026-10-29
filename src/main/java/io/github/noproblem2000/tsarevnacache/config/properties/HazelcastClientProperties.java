package io.github.noproblem2000.tsarevnacache.config.properties;

import com.hazelcast.client.config.ClientConnectionStrategyConfig.ReconnectMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection, retry and serialization settings of the Hazelcast client ({@code admin.hazelcast.*}).
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "admin.hazelcast")
public class HazelcastClientProperties {

    /** Name of the cluster to join; must match the members' {@code cluster-name}. */
    @NotBlank
    private String clusterName = "dev";

    /** Member addresses in {@code host:port} form. */
    @NotEmpty
    private List<String> members = new ArrayList<>(List.of("127.0.0.1:5701"));

    /** Optional client instance name, visible in member logs and diagnostics. */
    private String instanceName;

    /** Client labels, visible to members (e.g. in client statistics). */
    private List<String> labels = new ArrayList<>();

    /** Smart routing connects to every member and routes operations to partition owners. */
    private boolean smartRouting = true;

    /** Timeout of a single connection attempt. */
    @NotNull
    private Duration connectionTimeout = Duration.ofSeconds(5);

    /** Maximum time an invocation is retried before failing. */
    @NotNull
    private Duration invocationTimeout = Duration.ofSeconds(60);

    /** Interval of client heartbeats sent to members. */
    @NotNull
    private Duration heartbeatInterval = Duration.ofSeconds(5);

    /** A connection is closed when no message was received from a member for this period. */
    @NotNull
    private Duration heartbeatTimeout = Duration.ofSeconds(60);

    /** When {@code true} the application starts without waiting for the cluster connection. */
    private boolean asyncStart = true;

    /** Behaviour after the connection to the cluster is lost. */
    @NotNull
    private ReconnectMode reconnectMode = ReconnectMode.ASYNC;

    @Valid
    private Retry retry = new Retry();

    @Valid
    private Serialization serialization = new Serialization();

    /** Exponential backoff used while (re)connecting to the cluster. */
    @Getter
    @Setter
    public static class Retry {

        @NotNull
        private Duration initialBackoff = Duration.ofSeconds(1);

        @NotNull
        private Duration maxBackoff = Duration.ofSeconds(30);

        @DecimalMin("1.0")
        private double multiplier = 1.5;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double jitter = 0.2;

        /** Give up connecting after this period; {@code null} means retry forever. */
        private Duration clusterConnectTimeout;
    }

    /** Registration of user serialization artefacts needed to decode cluster data. */
    @Getter
    @Setter
    public static class Serialization {

        /** Jars or directories (all {@code *.jar} inside are added) holding DTOs and serializers. */
        private List<String> dtoClasspath = new ArrayList<>();

        /** Classes registered for zero-config Compact serialization. */
        private List<String> compactClasses = new ArrayList<>();

        /** {@code CompactSerializer} implementations (must have a no-arg constructor). */
        private List<String> compactSerializers = new ArrayList<>();

        /** Factory id to {@code DataSerializableFactory} class name. */
        private Map<Integer, String> dataSerializableFactories = new LinkedHashMap<>();

        /**
         * Factory ids served by the dynamic DTO schema registry ({@code IdentifiedDataSerializable}
         * without classes). A class configured in {@link #dataSerializableFactories} for the same id
         * becomes the fallback for class ids without a schema. Factory ids of registered schemas are
         * added automatically on start.
         */
        private List<Integer> dynamicFactoryIds = new ArrayList<>(List.of(1));

        /** Factory id to {@code PortableFactory} class name. */
        private Map<Integer, String> portableFactories = new LinkedHashMap<>();

        /** Portable version used for {@code ClassDefinition}s of the admin client. */
        private int portableVersion = 0;

        /** Custom {@code StreamSerializer}/{@code ByteArraySerializer} registrations. */
        @Valid
        private List<CustomSerializer> customSerializers = new ArrayList<>();
    }

    /** Binds a custom serializer to the type it handles. */
    @Getter
    @Setter
    public static class CustomSerializer {

        /** Fully qualified name of the serialized type. */
        @NotBlank
        private String typeClass;

        /** Fully qualified name of the serializer implementation. */
        @NotBlank
        private String serializerClass;
    }
}
