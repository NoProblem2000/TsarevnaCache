package io.github.noproblem2000.tsarevnacache.config;

import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.PortableFactory;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds {@link DataSerializableFactory} and {@link PortableFactory} implementations in the user's
 * libraries ({@code lib/} of the distribution, i.e. {@code -Dloader.path}, plus
 * {@code admin.hazelcast.serialization.dto-classpath}) so they are registered without any
 * configuration.
 *
 * <p>A class qualifies when its simple name contains {@code Factory}, it is a public concrete
 * class with a public no-arg constructor, implements one of the factory interfaces and declares
 * (or inherits) a {@code static int FACTORY_ID} constant, the convention of Hazelcast projects.
 * Classes are loaded without initialization; only qualifying factories are initialized to read
 * {@code FACTORY_ID}.
 */
@Slf4j
public final class FactoryDiscovery {

    /** System property of Spring Boot's {@code PropertiesLauncher} used by the start scripts. */
    public static final String LOADER_PATH = "loader.path";

    private static final String FACTORY_ID_FIELD = "FACTORY_ID";

    private FactoryDiscovery() {
    }

    /**
     * Discovered factories.
     *
     * @param dataSerializable factory id → {@code DataSerializableFactory} class name
     * @param portable         factory id → {@code PortableFactory} class name
     */
    public record Result(Map<Integer, String> dataSerializable, Map<Integer, String> portable) {

        public static Result empty() {
            return new Result(Map.of(), Map.of());
        }
    }

    /**
     * Scans the libraries of the DTO class loader and of {@code -Dloader.path}.
     *
     * @param classLoader loader used to load the candidate classes
     */
    public static Result discover(DtoClassLoader classLoader) {
        Set<Path> roots = new LinkedHashSet<>();
        for (URL url : classLoader.getURLs()) {
            toPath(url).ifPresent(roots::add);
        }
        String loaderPath = System.getProperty(LOADER_PATH);
        if (loaderPath != null) {
            for (String entry : loaderPath.split(",")) {
                if (!entry.isBlank()) {
                    roots.add(Path.of(entry.trim()).toAbsolutePath().normalize());
                }
            }
        }
        return discover(roots, classLoader);
    }

    /**
     * Scans the given jar files and directories (exploded classes and the jars directly inside).
     */
    public static Result discover(Collection<Path> roots, ClassLoader classLoader) {
        Map<Integer, String> dataSerializable = new TreeMap<>();
        Map<Integer, String> portable = new TreeMap<>();
        for (String className : candidateClassNames(roots)) {
            Class<?> type = load(className, classLoader);
            if (type == null || !isInstantiable(type)) {
                continue;
            }
            boolean ids = DataSerializableFactory.class.isAssignableFrom(type);
            boolean portableFactory = PortableFactory.class.isAssignableFrom(type);
            if (!ids && !portableFactory) {
                continue;
            }
            Integer factoryId = factoryId(type);
            if (factoryId == null) {
                log.info("Skipping {}: no static int FACTORY_ID constant", className);
                continue;
            }
            if (ids) {
                register(dataSerializable, factoryId, className, "DataSerializableFactory");
            }
            if (portableFactory) {
                register(portable, factoryId, className, "PortableFactory");
            }
        }
        return new Result(dataSerializable, portable);
    }

    private static void register(Map<Integer, String> target, int factoryId, String className, String kind) {
        String existing = target.putIfAbsent(factoryId, className);
        if (existing != null && !existing.equals(className)) {
            log.warn("Two {} classes declare FACTORY_ID {}: {} (used) and {} (ignored); configure the right one "
                    + "explicitly in admin.hazelcast.serialization", kind, factoryId, existing, className);
        }
    }

    private static List<String> candidateClassNames(Collection<Path> roots) {
        Set<String> names = new LinkedHashSet<>();
        for (Path root : roots) {
            if (Files.isDirectory(root)) {
                names.addAll(classesInDirectory(root));
                try (Stream<Path> files = Files.list(root)) {
                    files.filter(file -> file.getFileName().toString().toLowerCase().endsWith(".jar"))
                            .sorted()
                            .forEach(jar -> names.addAll(classesInJar(jar)));
                } catch (IOException e) {
                    log.warn("Cannot list {}: {}", root, e.getMessage());
                }
            } else if (Files.isRegularFile(root) && root.getFileName().toString().toLowerCase().endsWith(".jar")) {
                names.addAll(classesInJar(root));
            }
        }
        return new ArrayList<>(names);
    }

    private static List<String> classesInJar(Path jar) {
        List<String> names = new ArrayList<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (isCandidate(name)) {
                    names.add(toClassName(name));
                }
            }
        } catch (IOException e) {
            log.warn("Cannot read {}: {}", jar, e.getMessage());
        }
        return names;
    }

    private static List<String> classesInDirectory(Path directory) {
        try (Stream<Path> files = Files.walk(directory, 32)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> directory.relativize(file).toString().replace('\\', '/'))
                    .filter(FactoryDiscovery::isCandidate)
                    .map(FactoryDiscovery::toClassName)
                    .toList();
        } catch (IOException e) {
            log.warn("Cannot scan {}: {}", directory, e.getMessage());
            return List.of();
        }
    }

    /** {@code com/acme/OrderFactory.class} or a named nested class; no anonymous classes, no META-INF. */
    static boolean isCandidate(String entryName) {
        if (!entryName.endsWith(".class") || entryName.startsWith("META-INF/") || entryName.endsWith("module-info.class")) {
            return false;
        }
        String simple = entryName.substring(entryName.lastIndexOf('/') + 1, entryName.length() - ".class".length());
        String innermost = simple.substring(simple.lastIndexOf('$') + 1);
        return innermost.contains("Factory") && !innermost.isEmpty() && !Character.isDigit(innermost.charAt(0));
    }

    private static String toClassName(String entryName) {
        return entryName.substring(0, entryName.length() - ".class".length()).replace('/', '.');
    }

    private static Class<?> load(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            log.debug("Cannot load candidate factory {}: {}", className, e.toString());
            return null;
        }
    }

    private static boolean isInstantiable(Class<?> type) {
        int modifiers = type.getModifiers();
        if (type.isInterface() || Modifier.isAbstract(modifiers) || !Modifier.isPublic(modifiers)
                || (type.getEnclosingClass() != null && !Modifier.isStatic(modifiers))) {
            return false;
        }
        try {
            return Modifier.isPublic(type.getConstructor().getModifiers());
        } catch (NoSuchMethodException | LinkageError e) {
            return false;
        }
    }

    private static Integer factoryId(Class<?> type) {
        try {
            Field field = findField(type);
            if (field == null || !Modifier.isStatic(field.getModifiers())
                    || (field.getType() != int.class && field.getType() != Integer.class)) {
                return null;
            }
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof Integer id ? id : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            log.warn("Cannot read {}.FACTORY_ID: {}", type.getName(), e.toString());
            return null;
        }
    }

    private static Field findField(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(FACTORY_ID_FIELD);
            } catch (NoSuchFieldException ignored) {
                // look further up
            }
        }
        try {
            return type.getField(FACTORY_ID_FIELD); // public constant of an implemented interface
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static Optional<Path> toPath(URL url) {
        if (!"file".equals(url.getProtocol())) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(url.toURI()));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
