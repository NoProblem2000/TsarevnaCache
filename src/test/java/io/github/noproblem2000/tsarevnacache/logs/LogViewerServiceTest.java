package io.github.noproblem2000.tsarevnacache.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.config.properties.LogViewerProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

class LogViewerServiceTest {

    private static final String SPRING_LOG = String.join("\n",
            "2026-11-03T10:00:00.001+03:00  INFO 4242 --- [main] c.h.c.i.c.ClientConnectionManager : Connected",
            "2026-11-03T10:00:01.002+03:00  WARN 4242 --- [hz.client.event-1] c.h.c.i.s.ClientInvocation : Slow call",
            "2026-11-03T10:00:02.003+03:00 ERROR 4242 --- [http-nio-8080-exec-1] i.g.n.t.i.Inspector : Cannot read value",
            "com.hazelcast.nio.serialization.HazelcastSerializationException: No DataSerializerFactory registered",
            "\tat com.hazelcast.internal.serialization.impl.DataSerializableSerializer.read(DataSerializableSerializer.java:1)",
            "Caused by: java.lang.IllegalStateException: boom",
            "2026-11-03T10:00:03.004+03:00 DEBUG 4242 --- [main] c.h.c.i.s.ClientPartitionService : Partition table",
            "2026-11-03T10:00:04.005+03:00  INFO 4242 --- [main] i.g.n.t.TsarevnaCacheApplication : Started") + "\n";

    @TempDir
    Path directory;

    @Test
    void tailKeepsStackTracesWithTheirRecordAndFiltersByLevel() throws IOException {
        Path log = write("hazelcast-inspector.log", SPRING_LOG);
        LogViewerService service = service(log.toString());

        LogTailDTO all = service.tail(null, null, LogLevelFilter.ALL, null);
        assertThat(all.lines()).hasSize(8);
        assertThat(all.totalLines()).isEqualTo(8);
        assertThat(all.reachedStart()).isTrue();
        assertThat(all.lines().get(0).number()).isEqualTo(1);
        assertThat(all.lines().get(7).number()).isEqualTo(8);
        assertThat(all.lines().get(4).level()).isEqualTo(LogLevel.ERROR);
        assertThat(all.lines().get(4).continuation()).isTrue();
        assertThat(all.file().name()).isEqualTo("hazelcast-inspector.log");

        LogTailDTO errors = service.tail(log.toString(), 500, LogLevelFilter.ERROR, null);
        assertThat(errors.lines()).extracting(LogLineDTO::number).containsExactly(3L, 4L, 5L, 6L);
        assertThat(errors.lines()).allMatch(line -> line.level() == LogLevel.ERROR);
        assertThat(errors.lines().get(0).continuation()).isFalse();

        LogTailDTO warnings = service.tail(log.toString(), 500, LogLevelFilter.WARN, null);
        assertThat(warnings.lines()).extracting(LogLineDTO::number).containsExactly(2L, 3L, 4L, 5L, 6L);

        LogTailDTO info = service.tail(log.toString(), 500, LogLevelFilter.INFO, null);
        assertThat(info.lines()).extracting(LogLineDTO::number).doesNotContain(7L).contains(1L, 8L);

        LogTailDTO search = service.tail(log.toString(), 500, LogLevelFilter.ALL, "caused BY");
        assertThat(search.lines()).extracting(LogLineDTO::number).containsExactly(6L);
        assertThat(search.search()).isEqualTo("caused BY");

        LogTailDTO lastTwo = service.tail(log.toString(), 2, LogLevelFilter.ALL, null);
        assertThat(lastTwo.lines()).extracting(LogLineDTO::number).containsExactly(7L, 8L);
        assertThat(lastTwo.reachedStart()).isFalse();
    }

    @Test
    void readsOnlyTheEndOfLargeFilesAndNumbersFromTheEnd() throws IOException {
        StringBuilder text = new StringBuilder();
        IntStream.range(0, 20_000).forEach(i -> text.append("2026-11-03 10:00:00.000 INFO line ").append(i)
                .append(i % 1000 == 0 ? " ERROR-free marker" : "").append("\r\n"));
        Path log = write("big.log", text.toString());

        LogViewerProperties properties = properties(log.toString());
        properties.setLineNumberLimit(DataSize.ofKilobytes(1));
        LogViewerService service = new LogViewerService(properties, "");
        LogTailDTO tail = service.tail(null, 3, LogLevelFilter.ALL, null);
        assertThat(tail.totalLines()).isNull();
        assertThat(tail.lines()).extracting(LogLineDTO::number).containsExactly(-3L, -2L, -1L);
        assertThat(tail.lines().get(2).text()).isEqualTo("2026-11-03 10:00:00.000 INFO line 19999");
        assertThat(tail.scannedBytes()).isLessThan(Files.size(log));

        properties.setScanLimit(DataSize.ofKilobytes(64));
        LogTailDTO rare = service.tail(null, 10, LogLevelFilter.ALL, "marker");
        assertThat(rare.scanLimitReached()).isTrue();
        assertThat(rare.scannedBytes()).isLessThan(Files.size(log));
        assertThat(rare.lines()).allMatch(line -> line.text().contains("marker"));

        properties.setScanLimit(DataSize.ofMegabytes(64));
        LogTailDTO markers = service.tail(null, 100, LogLevelFilter.ALL, "marker");
        assertThat(markers.lines()).hasSize(20);
        assertThat(markers.reachedStart()).isTrue();
        assertThat(markers.lines().get(0).text()).endsWith("line 0 ERROR-free marker");
    }

    @Test
    void understandsTheTwoLineJavaUtilLoggingFormat() throws IOException {
        Path log = write("member.log", String.join("\n",
                "Nov 03, 2026 10:00:00 AM com.hazelcast.internal.cluster.ClusterService",
                "INFO: [127.0.0.1]:5701 [dev] [5.5.0] Members {size:1}",
                "Nov 03, 2026 10:00:05 AM com.hazelcast.spi.impl.operationservice.impl.Invocation",
                "SEVERE: [127.0.0.1]:5701 [dev] [5.5.0] Operation failed",
                "java.lang.IllegalStateException: boom",
                "\tat com.hazelcast.Example.run(Example.java:1)"));

        LogTailDTO errors = service(log.toString()).tail(null, null, LogLevelFilter.ERROR, null);
        assertThat(errors.lines()).extracting(LogLineDTO::number).containsExactly(3L, 4L, 5L, 6L);
        assertThat(errors.totalLines()).isEqualTo(6);

        // members started with a Russian locale write localized month names
        Path russian = write("member-ru.log", String.join("\n",
                "нояб. 03, 2026 4:03:48 PM com.hazelcast.internal.partition.impl.PartitionStateManagerImpl",
                "SEVERE: [192.168.3.29]:5701 [dev] [5.5.0] Partition table broken",
                "нояб. 03, 2026 4:03:49 PM com.hazelcast.core.LifecycleService",
                "INFO: [192.168.3.29]:5701 [dev] [5.5.0] is STARTED"));
        assertThat(service(russian.toString()).tail(null, null, LogLevelFilter.ERROR, null).lines())
                .extracting(LogLineDTO::number).containsExactly(1L, 2L);
    }

    @Test
    void cutsVeryLongLinesAndHandlesEmptyFiles() throws IOException {
        Path log = write("long.log", "2026-11-03 10:00:00 INFO " + "x".repeat(10_000) + "\nshort\n");
        LogViewerProperties properties = properties(log.toString());
        properties.setMaxLineLength(100);
        List<LogLineDTO> lines = new LogViewerService(properties, "").tail(null, null, LogLevelFilter.ALL, null)
                .lines();
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).text()).hasSize(102).endsWith(" …");
        assertThat(lines.get(1).text()).isEqualTo("short");
        assertThat(lines.get(1).level()).isEqualTo(LogLevel.INFO);

        Path empty = write("empty.log", "");
        LogTailDTO none = service(empty.toString()).tail(null, null, LogLevelFilter.ALL, null);
        assertThat(none.lines()).isEmpty();
        assertThat(none.totalLines()).isZero();
        assertThat(none.reachedStart()).isTrue();
    }

    @Test
    void onlyConfiguredFilesCanBeOpened() throws IOException {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.writeString(logs.resolve("hazelcast.log"), "a\n");
        Files.writeString(logs.resolve("hazelcast.log.1"), "b\n");
        Files.writeString(logs.resolve("notes.md"), "c\n");
        Files.writeString(logs.resolve("old.log.gz"), "d\n");
        Path secret = write("secret.txt", "password\n");
        Path own = logs.resolve("hazelcast-inspector.log");
        Files.writeString(own, "own\n");

        LogViewerService service = new LogViewerService(
                properties(logs.toString(), directory.resolve("missing.log").toString()), own.toString());
        List<LogFileDTO> files = service.files().files();
        assertThat(files).extracting(LogFileDTO::name)
                .containsExactlyInAnyOrder("hazelcast-inspector.log", "hazelcast.log", "hazelcast.log.1", "missing.log");
        assertThat(files.get(0).name()).isEqualTo("hazelcast-inspector.log");
        assertThat(files.get(0).applicationLog()).isTrue();
        assertThat(files.get(files.size() - 1).exists()).isFalse();

        assertThat(service.tail(logs.resolve("hazelcast.log.1").toString(), null, null, null).lines())
                .extracting(LogLineDTO::text).containsExactly("b");
        assertThat(service.tail(logs.resolve("x/../hazelcast.log").toString(), null, null, null).lines())
                .extracting(LogLineDTO::text).containsExactly("a");
        assertThatThrownBy(() -> service.tail(secret.toString(), null, null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.LOG_FILE_NOT_FOUND));
        assertThatThrownBy(() -> service.requireFile(logs.resolve("../secret.txt").toString()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.requireFile(logs.resolve("notes.md").toString()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.requireFile(directory.resolve("missing.log").toString()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void detectsLevelsOfCommonLayouts() {
        assertThat(LogLevel.detect("2026-11-03 10:00:00.000 [main] ERROR c.h.Foo - x")).isEqualTo(LogLevel.ERROR);
        assertThat(LogLevel.detect("WARNING: [127.0.0.1]:5701 slow")).isEqualTo(LogLevel.WARN);
        assertThat(LogLevel.detect("10:00:00,000 FINE  com.hazelcast")).isEqualTo(LogLevel.DEBUG);
        assertThat(LogLevel.detect("\tat com.hazelcast.ERRORS.read(ERRORS.java:1)")).isNull();
        assertThat(LogLevel.detect("plain text with error inside")).isNull();
        assertThat(LogLevelFilter.WARN.accepts(LogLevel.ERROR)).isTrue();
        assertThat(LogLevelFilter.WARN.accepts(LogLevel.INFO)).isFalse();
        assertThat(LogLevelFilter.ERROR.accepts(null)).isFalse();
        assertThat(LogLevelFilter.ALL.accepts(null)).isTrue();
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(directory.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static LogViewerService service(String... paths) {
        return new LogViewerService(properties(paths), "");
    }

    private static LogViewerProperties properties(String... paths) {
        LogViewerProperties properties = new LogViewerProperties();
        properties.setPaths(List.of(paths));
        return properties;
    }
}
