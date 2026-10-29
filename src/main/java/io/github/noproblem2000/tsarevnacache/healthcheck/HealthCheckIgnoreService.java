package io.github.noproblem2000.tsarevnacache.healthcheck;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.config.JsonFiles;
import io.github.noproblem2000.tsarevnacache.config.properties.HealthCheckProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Ignore list of the healthcheck, persisted in {@code config/healthcheck-ignores.json}. An entry
 * hides one issue ({@code MAP_UNBOUNDED_NO_TTL:countries}) or a whole rule
 * ({@code MAP_UNBOUNDED_NO_TTL:*}); hidden issues do not lower the health score.
 */
@Slf4j
@Service
public class HealthCheckIgnoreService {

    /** {@code CHECK_TYPE}, {@code CHECK_TYPE:*} or {@code CHECK_TYPE:<target>}. */
    private static final Pattern ISSUE_ID = Pattern.compile("[A-Z][A-Z0-9_]*(:.+)?", Pattern.DOTALL);
    private static final int MAX_ISSUE_ID = 1024;
    private static final int MAX_REASON = 2000;

    private final Path file;
    private final ObjectMapper mapper;
    private final Clock clock;
    private volatile List<HealthCheckIgnore> ignores;

    @Autowired
    public HealthCheckIgnoreService(HealthCheckProperties properties, ObjectMapper objectMapper) {
        this(Path.of(properties.getIgnoresFile()), objectMapper, Clock.systemUTC());
    }

    HealthCheckIgnoreService(Path file, ObjectMapper objectMapper, Clock clock) {
        this.file = file.toAbsolutePath().normalize();
        this.mapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.clock = clock;
        this.ignores = load();
        log.info("Healthcheck ignore list {}: {} entr{}", this.file, ignores.size(), ignores.size() == 1 ? "y" : "ies");
    }

    /** Entries in the order they were added. */
    public List<HealthCheckIgnore> list() {
        return ignores;
    }

    /** The entry hiding an issue, if any. */
    public Optional<HealthCheckIgnore> find(HealthCheckIssue issue) {
        return ignores.stream().filter(ignore -> ignore.matches(issue)).findFirst();
    }

    public Path file() {
        return file;
    }

    /**
     * Adds (or updates the reason of) an entry.
     *
     * @throws ApiException with {@link ErrorCode#VALIDATION_FAILED} for a malformed id or reason
     */
    public synchronized HealthCheckIgnore add(String issueId, String reason, String user) {
        String id = issueId == null ? "" : issueId.trim();
        String text = reason == null ? "" : reason.trim();
        if (id.length() > MAX_ISSUE_ID || !ISSUE_ID.matcher(id).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "issueId must be CHECK_TYPE, CHECK_TYPE:* or CHECK_TYPE:<target>", Map.of("issueId", id));
        }
        if (text.length() > MAX_REASON) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "reason is longer than " + MAX_REASON + " characters",
                    Map.of("reason", text.length()));
        }
        HealthCheckIgnore entry = new HealthCheckIgnore(id, text, Instant.now(clock), user);
        List<HealthCheckIgnore> next = new ArrayList<>(ignores);
        next.removeIf(existing -> existing.issueId().equals(id));
        next.add(entry);
        persist(next);
        return entry;
    }

    /**
     * Removes an entry.
     *
     * @throws ApiException with {@link ErrorCode#HEALTHCHECK_IGNORE_NOT_FOUND} when it does not exist
     */
    public synchronized void remove(String issueId) {
        List<HealthCheckIgnore> next = new ArrayList<>(ignores);
        if (!next.removeIf(existing -> existing.issueId().equals(issueId))) {
            throw new ApiException(ErrorCode.HEALTHCHECK_IGNORE_NOT_FOUND, "Not in the ignore list: " + issueId,
                    Map.of("issueId", String.valueOf(issueId)));
        }
        persist(next);
    }

    private List<HealthCheckIgnore> load() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            IgnoreFile content = mapper.readValue(file.toFile(), IgnoreFile.class);
            return content.ignores() == null ? List.of() : content.ignores().stream()
                    .filter(ignore -> ignore != null && ignore.issueId() != null && !ignore.issueId().isBlank())
                    .sorted(Comparator.comparing(HealthCheckIgnore::createdAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the healthcheck ignore list " + file + ": " + e.getMessage(), e);
        }
    }

    private void persist(List<HealthCheckIgnore> next) {
        try {
            JsonFiles.writeAtomically(mapper, file, new IgnoreFile(IgnoreFile.CURRENT_FORMAT, next), "healthcheck-ignores");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the healthcheck ignore list " + file, e);
        }
        ignores = List.copyOf(next);
    }

    /** Content of {@code healthcheck-ignores.json}. */
    record IgnoreFile(int formatVersion, List<HealthCheckIgnore> ignores) {
        static final int CURRENT_FORMAT = 1;
    }
}
