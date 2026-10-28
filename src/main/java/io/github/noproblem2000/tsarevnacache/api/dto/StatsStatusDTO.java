package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Availability of member metrics used for statistics.
 *
 * @param available   whether metrics were received from at least one member
 * @param collectedAt time of the newest metrics sample (epoch millis)
 * @param members     number of members that delivered metrics
 * @param errors      per-member problems, e.g. metrics disabled or member not answering
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StatsStatusDTO(boolean available, Long collectedAt, int members, List<String> errors) {
}
