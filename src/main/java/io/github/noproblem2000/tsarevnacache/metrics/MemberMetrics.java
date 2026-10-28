package io.github.noproblem2000.tsarevnacache.metrics;

import java.util.Map;
import java.util.UUID;

/**
 * The newest metrics sample of one member.
 *
 * @param uuid        member UUID
 * @param address     member address
 * @param collectedAt sample time (epoch millis)
 * @param objects     per-object metrics: prefix (e.g. {@code map}) → object name → metric → value
 * @param global      metrics without an object discriminator, keyed {@code prefix.metric}
 *                    (e.g. {@code runtime.usedMemory})
 */
public record MemberMetrics(
        UUID uuid,
        String address,
        long collectedAt,
        Map<String, Map<String, Map<String, Number>>> objects,
        Map<String, Number> global) {

    /** Metrics of one object, empty when the member reported none. */
    public Map<String, Number> of(String prefix, String objectName) {
        return objects.getOrDefault(prefix, Map.of()).getOrDefault(objectName, Map.of());
    }

    public Long global(String key) {
        Number value = global.get(key);
        return value == null ? null : value.longValue();
    }
}
