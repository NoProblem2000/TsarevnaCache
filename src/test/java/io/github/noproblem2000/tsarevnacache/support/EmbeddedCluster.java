package io.github.noproblem2000.tsarevnacache.support;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.internal.serialization.InternalSerializationService;
import com.hazelcast.spi.impl.SerializationServiceSupport;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.util.UUID;

/**
 * A single embedded Hazelcast member shared by all integration tests of a JVM. The member knows
 * the {@link HiddenClasses} (factories and custom serializer included); the admin client does not.
 */
public final class EmbeddedCluster {

    private static EmbeddedCluster instance;

    private final URLClassLoader hiddenClassLoader;
    private final HazelcastInstance member;
    private final String clusterName;

    private EmbeddedCluster() {
        this.hiddenClassLoader = HiddenClasses.compile();
        this.clusterName = "tsarevna-test-" + UUID.randomUUID();

        Config config = new Config();
        config.setClusterName(clusterName);
        config.setClassLoader(hiddenClassLoader);
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.setProperty("hazelcast.logging.type", "slf4j");
        config.getMetricsConfig().setCollectionFrequencySeconds(1);

        NetworkConfig network = config.getNetworkConfig();
        network.setPort(15701).setPortAutoIncrement(true).setPortCount(100);
        network.getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        JoinConfig join = network.getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");

        config.getSerializationConfig()
                .addDataSerializableFactoryClass(HiddenClasses.IDS_FACTORY_ID, "hidden.HiddenDataSerializableFactory")
                .addPortableFactoryClass(HiddenClasses.PORTABLE_FACTORY_ID, "hidden.HiddenPortableFactory")
                .addSerializerConfig(new SerializerConfig()
                        .setTypeClassName("hidden.HiddenCustom")
                        .setClassName("hidden.HiddenCustomSerializer"));

        this.member = Hazelcast.newHazelcastInstance(config);
    }

    public static synchronized EmbeddedCluster get() {
        if (instance == null) {
            instance = new EmbeddedCluster();
            Runtime.getRuntime().addShutdownHook(new Thread(instance.member::shutdown));
        }
        return instance;
    }

    public HazelcastInstance member() {
        return member;
    }

    public String clusterName() {
        return clusterName;
    }

    public String address() {
        InetSocketAddress address = member.getCluster().getLocalMember().getSocketAddress();
        return address.getHostString() + ":" + address.getPort();
    }

    public InternalSerializationService memberSerialization() {
        return (InternalSerializationService) ((SerializationServiceSupport) member).getSerializationService();
    }

    /** Instantiates a hidden class through its constructor matching the given argument types. */
    public Object newHidden(String simpleName, Class<?>[] types, Object... args) {
        try {
            Class<?> type = Class.forName("hidden." + simpleName, true, hiddenClassLoader);
            Constructor<?> constructor = type.getConstructor(types);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Reads a (private) field of an object. */
    public static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
