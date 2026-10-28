package io.github.noproblem2000.tsarevnacache.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limits and timeouts of the cluster/data explorer ({@code admin.explorer.*}).
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "admin.explorer")
public class ExplorerProperties {

    /** Timeout of a single cluster call issued by the explorer. */
    @NotNull
    private Duration operationTimeout = Duration.ofSeconds(15);

    /** How long member metrics (map/queue statistics, heap) are cached between UI refreshes. */
    @NotNull
    private Duration metricsCacheTtl = Duration.ofSeconds(3);

    /** Maximum page size of the IMap data browser. */
    @Min(1)
    @Max(1000)
    private int maxPageSize = 200;

    /** Number of partitions scanned in parallel while filling an unfiltered IMap page. */
    @Min(1)
    @Max(271)
    private int scanParallelism = 32;

    /** Maximum number of items shown for queues, sets, lists, multimaps and replicated maps. */
    @Min(1)
    @Max(500)
    private int maxPreviewItems = 50;

    /**
     * Queues, sets, multimaps and replicated maps can only be read as a whole by a client; their
     * items are not fetched when the structure is larger than this.
     */
    @Min(1)
    private int maxFetchItems = 10_000;
}
