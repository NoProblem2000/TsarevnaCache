package io.github.noproblem2000.tsarevnacache.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import com.fasterxml.jackson.databind.JsonNode;
import com.hazelcast.collection.IQueue;
import com.hazelcast.map.IMap;
import io.github.noproblem2000.tsarevnacache.support.AbstractApiTest;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Healthcheck against the embedded single member: real member metrics, sampled entries, the
 * ignore list file and the Prometheus endpoint.
 */
class HealthCheckApiTest extends AbstractApiTest {

    @BeforeAll
    static void createProblems() {
        var member = EmbeddedClusterHolder.member();
        IMap<String, String> unbounded = member.getMap("hc-unbounded");
        Map<String, String> batch = new HashMap<>();
        for (int i = 0; i < 1_100; i++) {
            batch.put("k" + i, "value-" + i);
        }
        unbounded.putAll(batch);

        IMap<String, Integer> zombie = member.getMap("hc-zombie");
        for (int i = 0; i < 150; i++) {
            zombie.put("z" + i, i);
        }

        IQueue<Integer> queue = member.getQueue("hc-backlog");
        for (int i = 0; i < 5_001; i++) {
            queue.offer(i);
        }
        member.getMap("hc-dto").put("u", EmbeddedClusterHolder.newMemberObject("prod.model.UnknownDTO"));
    }

    /** Runs the checks until the report satisfies the condition (member metrics refresh every second). */
    private JsonNode runUntil(Predicate<JsonNode> condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            Response response = httpPost("/api/healthcheck/run");
            assertThat(response.status()).isEqualTo(200);
            last = response.body();
            if (condition.test(last)) {
                return last;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Condition not met, last report: " + last);
    }

    private static JsonNode issue(JsonNode report, String list, String issueId) {
        for (JsonNode issue : report.path(list)) {
            if (issueId.equals(issue.path("issueId").asText())) {
                return issue;
            }
        }
        return null;
    }

    private static Predicate<JsonNode> active(String... issueIds) {
        return report -> {
            for (String id : issueIds) {
                if (issue(report, "activeIssues", id) == null) {
                    return false;
                }
            }
            return true;
        };
    }

    @Test
    void problemsOfTheClusterAreFound() throws Exception {
        JsonNode report = runUntil(active("CLUSTER_SINGLE_MEMBER:Cluster", "MAP_UNBOUNDED_NO_TTL:hc-unbounded",
                "MAP_ZOMBIE_WRITE_ONLY:hc-zombie", "QUEUE_BACKLOG_GROWTH:hc-backlog", "DTO_UNREGISTERED_SCHEMA:1:3"));

        assertThat(report.path("clusterAvailable").asBoolean()).isTrue();
        assertThat(report.path("status").asText()).isIn("DEGRADED", "CRITICAL");
        assertThat(report.path("healthScore").asInt()).isBetween(0, 99);
        assertThat(report.path("lastRunTime").asText()).isNotEmpty();
        assertThat(report.path("counts").path("warning").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(report.path("checkedMaps").asInt()).isGreaterThanOrEqualTo(3);

        JsonNode unbounded = issue(report, "activeIssues", "MAP_UNBOUNDED_NO_TTL:hc-unbounded");
        assertThat(unbounded.path("severity").asText()).isEqualTo("WARNING");
        assertThat(unbounded.path("targetType").asText()).isEqualTo("IMAP");
        assertThat(unbounded.path("targetName").asText()).isEqualTo("hc-unbounded");
        assertThat(unbounded.path("currentValue").asText()).contains("entries=1100", "ttl=infinite");
        assertThat(unbounded.path("recommendationCode").asText()).isEqualTo("MAP_UNBOUNDED_NO_TTL");

        JsonNode queue = issue(report, "activeIssues", "QUEUE_BACKLOG_GROWTH:hc-backlog");
        assertThat(queue.path("details").path("size").asLong()).isEqualTo(5_001);

        JsonNode dto = issue(report, "activeIssues", "DTO_UNREGISTERED_SCHEMA:1:3");
        assertThat(dto.path("targetType").asText()).isEqualTo("DTO_SCHEMA");
        assertThat(dto.path("details").path("classId").asInt()).isEqualTo(3);
        assertThat(dto.path("details").path("maps").toString()).contains("hc-dto");

        // the detection time stays the time of the first run while the issue persists
        String detectedAt = unbounded.path("detectedAt").asText();
        JsonNode again = httpPost("/api/healthcheck/run").body();
        assertThat(issue(again, "activeIssues", "MAP_UNBOUNDED_NO_TTL:hc-unbounded").path("detectedAt").asText())
                .isEqualTo(detectedAt);
        assertThat(httpGet("/api/healthcheck/report").body().path("lastRunTime").asText())
                .isEqualTo(again.path("lastRunTime").asText());
    }

    @Test
    void ignoredIssuesMoveOutOfTheScoreAndBack() throws Exception {
        JsonNode before = runUntil(active("QUEUE_BACKLOG_GROWTH:hc-backlog", "CLUSTER_SINGLE_MEMBER:Cluster"));
        int score = before.path("healthScore").asInt();

        JsonNode ignored = httpPostJson("/api/healthcheck/ignore",
                Map.of("issueId", "QUEUE_BACKLOG_GROWTH:hc-backlog", "reason", "batch import queue")).body();
        assertThat(issue(ignored, "activeIssues", "QUEUE_BACKLOG_GROWTH:hc-backlog")).isNull();
        assertThat(issue(ignored, "ignoredIssues", "QUEUE_BACKLOG_GROWTH:hc-backlog")).isNotNull();
        assertThat(ignored.path("healthScore").asInt()).isEqualTo(Math.min(100, score + 10));
        assertThat(ignored.path("ignores").toString()).contains("batch import queue", "\"createdBy\":\"admin\"");
        assertThat(Files.readString(HEALTHCHECK_IGNORES_FILE)).contains("QUEUE_BACKLOG_GROWTH:hc-backlog");

        // a whole rule, removed through the query variant
        JsonNode rule = httpPostJson("/api/healthcheck/ignore", Map.of("issueId", "CLUSTER_SINGLE_MEMBER:*")).body();
        assertThat(issue(rule, "ignoredIssues", "CLUSTER_SINGLE_MEMBER:Cluster")).isNotNull();
        assertThat(httpDelete("/api/healthcheck/ignore?issueId={id}", "CLUSTER_SINGLE_MEMBER:*").status()).isEqualTo(200);

        JsonNode restored = httpDelete("/api/healthcheck/ignore/QUEUE_BACKLOG_GROWTH:hc-backlog").body();
        assertThat(issue(restored, "activeIssues", "QUEUE_BACKLOG_GROWTH:hc-backlog")).isNotNull();
        assertThat(restored.path("ignores").size()).isZero();

        assertThat(httpDelete("/api/healthcheck/ignore/QUEUE_BACKLOG_GROWTH:hc-backlog").code())
                .isEqualTo("HEALTHCHECK_IGNORE_NOT_FOUND");
        assertThat(httpPostJson("/api/healthcheck/ignore", Map.of("issueId", "not valid")).code())
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void prometheusExporterServesTheTextFormat() throws Exception {
        runUntil(active("MAP_UNBOUNDED_NO_TTL:hc-unbounded"));
        MockHttpServletResponse response = mvc.perform(MockMvcRequestBuilders.get("/api/healthcheck/prometheus")
                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("text/plain").contains("version=0.0.4");
        String text = response.getContentAsString();
        assertThat(text).contains("hazelcast_inspector_health_score ",
                "hazelcast_inspector_health_issues_total{severity=\"WARNING\"} ",
                "hazelcast_inspector_map_entries_total{map=\"hc-unbounded\"} 1100",
                "hazelcast_inspector_map_memory_bytes{map=\"hc-unbounded\",type=\"owned\"} ",
                "hazelcast_inspector_cluster_up 1");

        assertThat(mvc.perform(MockMvcRequestBuilders.get("/api/healthcheck/prometheus")).andReturn()
                .getResponse().getStatus()).isEqualTo(401);
    }

    /** Static access for {@link BeforeAll} (the instance helper {@code cluster()} needs an instance). */
    private static final class EmbeddedClusterHolder {
        static com.hazelcast.core.HazelcastInstance member() {
            return io.github.noproblem2000.tsarevnacache.support.EmbeddedCluster.get().member();
        }

        static Object newMemberObject(String className) {
            return io.github.noproblem2000.tsarevnacache.support.EmbeddedCluster.get().newMemberObject(className);
        }
    }
}
