package io.github.noproblem2000.tsarevnacache.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import com.fasterxml.jackson.databind.JsonNode;
import com.hazelcast.core.HazelcastInstance;
import io.github.noproblem2000.tsarevnacache.support.AbstractApiTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class LogApiTest extends AbstractApiTest {

    @Autowired
    private HazelcastInstance hazelcastClient;

    @Test
    void tailsAndDownloadsConfiguredLogFilesOnly() throws Exception {
        Files.createDirectories(LOGS_DIRECTORY);
        Path member = LOGS_DIRECTORY.resolve("member-1.log");
        Files.writeString(member, String.join("\n",
                "2026-11-03 10:00:00.000 INFO  [hz.member-1.generic-operation.thread-1] c.h.i.c.ClusterService : Members {size:2}",
                "2026-11-03 10:00:01.000 ERROR [hz.member-1.IO.thread-in-0] c.h.i.s.SerializationService : Failed",
                "com.hazelcast.nio.serialization.HazelcastSerializationException: There is no suitable de-serializer",
                "2026-11-03 10:00:02.000 WARN  [hz.member-1.cached.thread-2] c.h.s.i.PartitionService : Slow migration"),
                StandardCharsets.UTF_8);
        Files.writeString(LOGS_DIRECTORY.resolve("notes.md"), "not a log");

        JsonNode files = httpGet("/api/logs/files").body();
        assertThat(files.path("defaultLines").asInt()).isEqualTo(500);
        assertThat(files.path("maxLines").asInt()).isEqualTo(5000);
        String id = null;
        for (JsonNode file : files.path("files")) {
            assertThat(file.path("name").asText()).doesNotEndWith(".md");
            if (file.path("name").asText().equals("member-1.log")) {
                id = file.path("id").asText();
                assertThat(file.path("exists").asBoolean()).isTrue();
                assertThat(file.path("size").asLong()).isEqualTo(Files.size(member));
            }
        }
        assertThat(id).isNotNull();

        JsonNode errors = httpGet("/api/logs/tail?file={file}&level=ERROR", id).body();
        assertThat(errors.path("lines").size()).isEqualTo(2);
        assertThat(errors.path("lines").get(0).path("number").asLong()).isEqualTo(2);
        assertThat(errors.path("lines").get(1).path("level").asText()).isEqualTo("ERROR");
        assertThat(errors.path("lines").get(1).path("continuation").asBoolean()).isTrue();
        assertThat(errors.path("totalLines").asLong()).isEqualTo(4);

        JsonNode search = httpGet("/api/logs/tail?file={file}&search={search}&lines=1", id, "thread").body();
        assertThat(search.path("lines").size()).isEqualTo(1);
        assertThat(search.path("lines").get(0).path("number").asLong()).isEqualTo(4);
        assertThat(search.path("requestedLines").asInt()).isEqualTo(1);
        assertThat(httpGet("/api/logs/tail?file={file}&lines=999999", id).body().path("requestedLines").asInt())
                .isEqualTo(5000);

        MockHttpServletResponse download = mvc.perform(MockMvcRequestBuilders.get("/api/logs/download")
                .param("file", id).with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))).andReturn().getResponse();
        assertThat(download.getStatus()).isEqualTo(200);
        assertThat(download.getHeader("Content-Disposition")).contains("attachment").contains("member-1.log");
        assertThat(download.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(Files.readString(member));

        Path outside = Files.writeString(LOGS_DIRECTORY.resolveSibling("outside.log"), "secret");
        Response denied = httpGet("/api/logs/tail?file={file}", outside.toString());
        assertThat(denied.status()).isEqualTo(404);
        assertThat(denied.code()).isEqualTo("LOG_FILE_NOT_FOUND");
        assertThat(httpGet("/api/logs/download?file={file}", LOGS_DIRECTORY.resolve("../outside.log").toString())
                .code()).isEqualTo("LOG_FILE_NOT_FOUND");
        assertThat(httpGet("/api/logs/tail?level=SEVERE").status()).isEqualTo(400);
    }

    @Test
    void liveStreamRecordsClusterAndHazelcastLogEvents() throws Exception {
        long start = httpGet("/api/logs/live").body().path("lastSeq").asLong();

        hazelcastClient.getMap("live-stream-map").put("k", "v");
        LoggerFactory.getLogger("com.hazelcast.test.LiveStream").warn("Serialization warning for the live stream");
        LoggerFactory.getLogger("com.hazelcast.test.LiveStream").debug("not captured: below INFO");
        LoggerFactory.getLogger(LogApiTest.class).info("not captured: application events start at WARN");
        LoggerFactory.getLogger("io.github.noproblem2000.tsarevnacache.test").error("Application failure",
                new IllegalStateException("boom"));

        JsonNode created = awaitLive(start, "", event -> event.path("source").asText().equals("DISTRIBUTED_OBJECT")
                && event.path("message").asText().contains("live-stream-map"));
        assertThat(created.path("level").asText()).isEqualTo("INFO");

        JsonNode warning = awaitLive(start, "", event -> event.path("message").asText()
                .equals("Serialization warning for the live stream"));
        assertThat(warning.path("source").asText()).isEqualTo("LOG");
        assertThat(warning.path("level").asText()).isEqualTo("WARN");
        assertThat(warning.path("logger").asText()).isEqualTo("com.hazelcast.test.LiveStream");

        JsonNode failure = awaitLive(start, "&level=ERROR", event -> event.path("message").asText()
                .equals("Application failure"));
        assertThat(failure.path("stackTrace").asText()).contains("IllegalStateException: boom");

        JsonNode all = httpGet("/api/logs/live?after=" + start).body();
        for (JsonNode event : all.path("events")) {
            assertThat(event.path("seq").asLong()).isGreaterThan(start);
            assertThat(event.path("message").asText()).doesNotStartWith("not captured");
        }
        assertThat(all.path("capacity").asInt()).isEqualTo(1000);

        JsonNode searched = httpGet("/api/logs/live?after={after}&search={search}", start, "SERIALIZATION WARNING")
                .body();
        assertThat(searched.path("events").size()).isEqualTo(1);

        long last = all.path("lastSeq").asLong();
        assertThat(httpGet("/api/logs/live?after=" + last).body().path("events").size()).isZero();

        assertThat(httpDelete("/api/logs/live").status()).isEqualTo(204);
        JsonNode cleared = httpGet("/api/logs/live").body();
        assertThat(cleared.path("lastSeq").asLong()).isGreaterThanOrEqualTo(last);
        for (JsonNode event : cleared.path("events")) {
            assertThat(event.path("seq").asLong()).isGreaterThan(last);
        }
    }

    /** Polls the live stream until an event newer than {@code after} matches. */
    private JsonNode awaitLive(long after, String filter, Predicate<JsonNode> condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            for (JsonNode event : httpGet("/api/logs/live?after=" + after + filter).body().path("events")) {
                if (condition.test(event)) {
                    return event;
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("No matching live event after " + after);
    }
}
