package io.github.noproblem2000.tsarevnacache.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.hazelcast.core.HazelcastInstance;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FactoryDiscoveryTest {

    private static final Map<String, String> SOURCES = Map.of(
            "acme/Ids.java", """
                    package acme;
                    public interface Ids { int FACTORY_ID = 7; }
                    """,
            "acme/OrderFactory.java", """
                    package acme;
                    public class OrderFactory implements com.hazelcast.nio.serialization.DataSerializableFactory {
                        public static final int FACTORY_ID = 42;
                        public com.hazelcast.nio.serialization.IdentifiedDataSerializable create(int id) { return null; }
                    }
                    """,
            "acme/LegacyPortableFactory.java", """
                    package acme;
                    public class LegacyPortableFactory implements com.hazelcast.nio.serialization.PortableFactory, Ids {
                        public com.hazelcast.nio.serialization.Portable create(int id) { return null; }
                    }
                    """,
            "acme/NoIdFactory.java", """
                    package acme;
                    public class NoIdFactory implements com.hazelcast.nio.serialization.DataSerializableFactory {
                        public com.hazelcast.nio.serialization.IdentifiedDataSerializable create(int id) { return null; }
                    }
                    """,
            "acme/AbstractFactory.java", """
                    package acme;
                    public abstract class AbstractFactory implements com.hazelcast.nio.serialization.DataSerializableFactory {
                        public static final int FACTORY_ID = 99;
                    }
                    """);

    @TempDir
    Path dir;

    @Test
    void factoriesInJarsAreFoundByTheirFactoryId() throws Exception {
        Path classes = compile();
        Path lib = Files.createDirectories(dir.resolve("lib"));
        Path jar = jar(classes, lib.resolve("acme-dto.jar"));

        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, getClass().getClassLoader())) {
            FactoryDiscovery.Result result = FactoryDiscovery.discover(List.of(lib), loader);
            assertThat(result.dataSerializable()).containsExactly(Map.entry(42, "acme.OrderFactory"));
            assertThat(result.portable()).containsExactly(Map.entry(7, "acme.LegacyPortableFactory"));
        }
    }

    @Test
    void explodedClassDirectoriesAreScannedToo() throws Exception {
        Path classes = compile();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader())) {
            assertThat(FactoryDiscovery.discover(List.of(classes), loader).dataSerializable()).containsKey(42);
        }
    }

    @Test
    void onlyNamedFactoryClassesAreCandidates() {
        assertThat(FactoryDiscovery.isCandidate("com/acme/OrderFactory.class")).isTrue();
        assertThat(FactoryDiscovery.isCandidate("com/acme/Outer$InnerFactory.class")).isTrue();
        assertThat(FactoryDiscovery.isCandidate("com/acme/OrderFactory$1.class")).isFalse();
        assertThat(FactoryDiscovery.isCandidate("com/acme/Order.class")).isFalse();
        assertThat(FactoryDiscovery.isCandidate("META-INF/versions/11/acme/XFactory.class")).isFalse();
        assertThat(FactoryDiscovery.isCandidate("module-info.class")).isFalse();
    }

    private Path compile() throws IOException {
        Path sources = Files.createDirectories(dir.resolve("src"));
        Path classes = Files.createDirectories(dir.resolve("classes"));
        List<String> args = new ArrayList<>(List.of("-d", classes.toString(), "-classpath", hazelcastJar(), "-proc:none"));
        for (Map.Entry<String, String> source : SOURCES.entrySet()) {
            Path file = sources.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            args.add(file.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new))).isZero();
        return classes;
    }

    private static Path jar(Path classes, Path target) throws IOException {
        try (OutputStream file = Files.newOutputStream(target);
             JarOutputStream jar = new JarOutputStream(file);
             Stream<Path> files = Files.walk(classes)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                jar.putNextEntry(new JarEntry(classes.relativize(path).toString().replace('\\', '/')));
                jar.write(Files.readAllBytes(path));
                jar.closeEntry();
            }
        }
        return target;
    }

    private static String hazelcastJar() {
        try {
            return Path.of(HazelcastInstance.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
