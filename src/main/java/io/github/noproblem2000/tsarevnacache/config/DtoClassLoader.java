package io.github.noproblem2000.tsarevnacache.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Isolated class loader holding user supplied DTOs, factories and serializers.
 *
 * <p>It is shared by the Hazelcast client (to decode cluster data) and by the value inspector (to
 * convert JSON into {@code CLASS} typed values). Without configured paths it simply delegates to
 * the application class loader.
 */
@Slf4j
public final class DtoClassLoader extends URLClassLoader {

    static {
        registerAsParallelCapable();
    }

    private DtoClassLoader(URL[] urls, ClassLoader parent) {
        super("tsarevna-dto", urls, parent);
    }

    /**
     * Creates a loader from jar files and directories. A directory contributes itself (for exploded
     * classes) plus every {@code *.jar} directly inside it.
     *
     * @param paths  configured classpath entries; blank entries are ignored
     * @param parent parent class loader
     * @return the new loader
     * @throws IllegalStateException if a configured path does not exist
     */
    public static DtoClassLoader create(Collection<String> paths, ClassLoader parent) {
        List<URL> urls = new ArrayList<>();
        for (String raw : paths) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Path path = Path.of(raw.trim()).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                throw new IllegalStateException("DTO classpath entry does not exist: " + path);
            }
            if (Files.isDirectory(path)) {
                urls.add(toUrl(path));
                urls.addAll(jarsIn(path));
            } else {
                urls.add(toUrl(path));
            }
        }
        urls.forEach(url -> log.info("DTO classpath entry: {}", url));
        return new DtoClassLoader(urls.toArray(URL[]::new), parent);
    }

    /**
     * Loads a class through this loader.
     *
     * @param className fully qualified class name
     * @return the class
     * @throws ClassNotFoundException if neither this loader nor its parents know the class
     */
    public Class<?> load(String className) throws ClassNotFoundException {
        return Class.forName(className.trim(), false, this);
    }

    private static List<URL> jarsIn(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .sorted()
                    .map(DtoClassLoader::toUrl)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list DTO directory " + directory, e);
        }
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid DTO classpath entry " + path, e);
        }
    }
}
