package io.github.noproblem2000.tsarevnacache.healthcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HealthCheckIgnoreServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private HealthCheckIgnoreService open() {
        return new HealthCheckIgnoreService(dir.resolve("config/healthcheck-ignores.json"), MAPPER, CLOCK);
    }

    private static HealthCheckIssue issue(HealthCheckType type, String target) {
        return new HealthCheckIssue(type.issueId(target), type, HealthSeverity.WARNING, type.targetType(), target,
                "x", type.name(), Instant.EPOCH, Map.of());
    }

    @Test
    void entriesArePersistedAndMatchExactIdsOrWholeRules() throws Exception {
        HealthCheckIgnoreService service = open();
        assertThat(service.list()).isEmpty();
        service.add("MAP_UNBOUNDED_NO_TTL:countries", "  static reference data ", "admin");
        service.add("QUEUE_BACKLOG_GROWTH:*", "batch queues", "admin");
        service.add("CLUSTER_SINGLE_MEMBER", null, null);

        String json = Files.readString(service.file());
        assertThat(json).contains("\"MAP_UNBOUNDED_NO_TTL:countries\"", "\"static reference data\"", "2026-10-29T10:00:00Z");

        HealthCheckIgnoreService reloaded = open();
        assertThat(reloaded.list()).extracting(HealthCheckIgnore::issueId)
                .containsExactly("MAP_UNBOUNDED_NO_TTL:countries", "QUEUE_BACKLOG_GROWTH:*", "CLUSTER_SINGLE_MEMBER");
        assertThat(reloaded.find(issue(HealthCheckType.MAP_UNBOUNDED_NO_TTL, "countries"))).isPresent();
        assertThat(reloaded.find(issue(HealthCheckType.MAP_UNBOUNDED_NO_TTL, "orders"))).isEmpty();
        assertThat(reloaded.find(issue(HealthCheckType.QUEUE_BACKLOG_GROWTH, "jobs"))).isPresent();
        assertThat(reloaded.find(issue(HealthCheckType.CLUSTER_SINGLE_MEMBER, "Cluster"))).isPresent();
        assertThat(reloaded.list().get(1).wholeRule()).isTrue();
        assertThat(reloaded.list().get(0).wholeRule()).isFalse();
    }

    @Test
    void addingTwiceUpdatesTheReasonAndRemoveDeletes() {
        HealthCheckIgnoreService service = open();
        service.add("MAP_HIGH_LATENCY:a/b", "first", "admin");
        service.add("MAP_HIGH_LATENCY:a/b", "second", "admin");
        assertThat(service.list()).singleElement().satisfies(entry -> assertThat(entry.reason()).isEqualTo("second"));

        service.remove("MAP_HIGH_LATENCY:a/b");
        assertThat(open().list()).isEmpty();
        assertThatThrownBy(() -> service.remove("MAP_HIGH_LATENCY:a/b")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.HEALTHCHECK_IGNORE_NOT_FOUND));
    }

    @Test
    void malformedIdsAreRejected() {
        HealthCheckIgnoreService service = open();
        for (String bad : new String[] {"", "  ", "lower:case", ":map", "MAP:"}) {
            assertThatThrownBy(() -> service.add(bad, "r", "admin")).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        assertThat(service.list()).isEmpty();
    }
}
