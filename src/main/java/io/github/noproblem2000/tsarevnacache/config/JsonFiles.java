package io.github.noproblem2000.tsarevnacache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes the service's own JSON files ({@code config/dto-schemas.json},
 * {@code config/healthcheck-ignores.json}) so that a crash never leaves a half written file:
 * the content goes to a temporary file in the same directory that then replaces the target.
 */
public final class JsonFiles {

    private JsonFiles() {
    }

    /**
     * @param prefix prefix of the temporary file
     * @throws IOException when the directory or the file cannot be written
     */
    public static void writeAtomically(ObjectMapper mapper, Path file, Object content, String prefix) throws IOException {
        Path directory = file.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }
        Path temp = Files.createTempFile(directory != null ? directory : Path.of("."), prefix, ".tmp");
        try {
            mapper.writeValue(temp.toFile(), content);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
