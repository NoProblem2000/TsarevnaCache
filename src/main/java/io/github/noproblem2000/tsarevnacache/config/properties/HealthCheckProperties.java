package io.github.noproblem2000.tsarevnacache.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Automated healthcheck and best practices analyzer ({@code admin.healthcheck.*}).
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "admin.healthcheck")
public class HealthCheckProperties {

    /** Runs the checks in the background; "Run Check Now" and the REST API work either way. */
    private boolean enabled = true;

    /** Period of the background runs in milliseconds. */
    @Min(5_000)
    private long intervalMs = 60_000;

    /** Delay of the first background run after start in milliseconds. */
    @Min(0)
    private long initialDelayMs = 15_000;

    /**
     * Ignore list. Relative paths resolve against the working directory (the distribution root when
     * started with the bundled launchers).
     */
    @NotBlank
    private String ignoresFile = "./config/healthcheck-ignores.json";

    /** Entries read per map to inspect TTL / max idle and unregistered DTOs. */
    @Min(1)
    @Max(200)
    private int sampleSize = 20;

    /** Maps whose entries are sampled per run (the largest by memory first). */
    @Min(0)
    @Max(10_000)
    private int maxSampledMaps = 100;

    @Valid
    @NotNull
    private Thresholds thresholds = new Thresholds();

    /** Limits of the individual rules. */
    @Getter
    @Setter
    public static class Thresholds {

        /** MAP_UNBOUNDED_NO_TTL: map size above which entries without TTL / max idle are reported. */
        @Min(1)
        private long unboundedMinEntries = 1_000;

        /** MAP_UNBOUNDED_NO_TTL: owned memory above which the map is reported regardless of its size. */
        @NotNull
        private DataSize unboundedWarningMemory = DataSize.ofMegabytes(50);

        /** MAP_UNBOUNDED_NO_TTL: owned memory that makes the finding CRITICAL. */
        @NotNull
        private DataSize unboundedCriticalMemory = DataSize.ofMegabytes(250);

        /** MAP_ZOMBIE_WRITE_ONLY: minimum number of entries of a write-only map. */
        @Min(0)
        private long zombieMinEntries = 100;

        /** MAP_HIGH_LATENCY: average get / put latency in milliseconds above which a map is reported. */
        @Min(1)
        private long latencyMs = 25;

        /** MAP_HIGH_LATENCY: operations needed before an average is meaningful. */
        @Min(1)
        private long latencyMinOperations = 50;

        /** QUEUE_BACKLOG_GROWTH: queue size above which a backlog is reported. */
        @Min(1)
        private long queueBacklog = 5_000;
    }
}
