package io.github.noproblem2000.tsarevnacache.metrics;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.codec.MCReadMetricsCodec;
import com.hazelcast.cluster.Member;
import com.hazelcast.internal.metrics.MetricConsumer;
import com.hazelcast.internal.metrics.MetricDescriptor;
import com.hazelcast.internal.metrics.impl.MetricsCompressor;
import io.github.noproblem2000.tsarevnacache.config.properties.ExplorerProperties;
import io.github.noproblem2000.tsarevnacache.hazelcast.ClientAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reads member metrics through the Management Center client protocol.
 *
 * <p>Hazelcast clients cannot call {@code IMap.getLocalMapStats()}. Members however publish their
 * metrics (map, queue, multimap, replicated map statistics, JVM heap, ...) to a small buffer that
 * Management Center reads with {@code MCReadMetricsCodec}; this service does the same, keeps only
 * the newest sample per member and caches the result for {@code admin.explorer.metrics-cache-ttl}.
 *
 * <p>Requirements on the members: metrics enabled (default) including the Management Center
 * consumer ({@code hazelcast.metrics.management-center.enabled}, default {@code true}). A new
 * sample is produced every {@code hazelcast.metrics.collection.frequency} seconds (default 5).
 */
@Slf4j
@Service
public class ClusterMetricsService {

    private static final Set<String> KEPT_OBJECT_PREFIXES = Set.of(
            ClusterMetrics.MAP, ClusterMetrics.MAP_NEAR_CACHE, ClusterMetrics.MULTI_MAP,
            ClusterMetrics.REPLICATED_MAP, ClusterMetrics.QUEUE);
    private static final Set<String> KEPT_GLOBAL_PREFIXES = Set.of("runtime", "memory");

    private final ClientAccess clientAccess;
    private final ExplorerProperties properties;
    private final Object refreshLock = new Object();
    private volatile Cached cached;

    public ClusterMetricsService(ClientAccess clientAccess, ExplorerProperties properties) {
        this.clientAccess = clientAccess;
        this.properties = properties;
    }

    /** Returns cached metrics, refreshing them when older than the configured TTL. */
    public ClusterMetrics current() {
        Cached snapshot = cached;
        if (snapshot != null && !snapshot.isExpired(properties.getMetricsCacheTtl().toMillis())) {
            return snapshot.metrics();
        }
        synchronized (refreshLock) {
            snapshot = cached;
            if (snapshot == null || snapshot.isExpired(properties.getMetricsCacheTtl().toMillis())) {
                snapshot = new Cached(read(), System.currentTimeMillis());
                cached = snapshot;
            }
            return snapshot.metrics();
        }
    }

    /** Drops the cache so the next call reads fresh metrics (used after data modifications). */
    public void invalidate() {
        cached = null;
    }

    private ClusterMetrics read() {
        if (!clientAccess.isConnected()) {
            return new ClusterMetrics(List.of(), List.of("Client is not connected to the cluster"));
        }
        List<Member> members = new ArrayList<>(clientAccess.hazelcast().getCluster().getMembers());
        List<CompletableFuture<ClientMessage>> futures = members.stream()
                .map(member -> clientAccess.invokeOnMemberAsync(
                        MCReadMetricsCodec.encodeRequest(member.getUuid(), 0), member.getUuid()))
                .toList();

        List<MemberMetrics> result = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            String address = member.getAddress().getHost() + ":" + member.getAddress().getPort();
            try {
                MCReadMetricsCodec.ResponseParameters response =
                        MCReadMetricsCodec.decodeResponse(clientAccess.await(futures.get(i)));
                if (response.elements == null || response.elements.isEmpty()) {
                    errors.add(address + ": no metrics published yet (metrics disabled or first sample pending)");
                    continue;
                }
                Map.Entry<Long, byte[]> newest = response.elements.get(response.elements.size() - 1);
                result.add(decode(member, address, newest.getKey(), newest.getValue()));
            } catch (RuntimeException e) {
                log.debug("Cannot read metrics of member {}", address, e);
                errors.add(address + ": " + e.getMessage());
            }
        }
        return new ClusterMetrics(List.copyOf(result), List.copyOf(errors));
    }

    private static MemberMetrics decode(Member member, String address, long timestamp, byte[] blob) {
        Map<String, Map<String, Map<String, Number>>> objects = new HashMap<>();
        Map<String, Number> global = new HashMap<>();
        MetricsCompressor.extractMetrics(blob, new MetricConsumer() {
            @Override
            public void consumeLong(MetricDescriptor descriptor, long value) {
                accept(descriptor, value);
            }

            @Override
            public void consumeDouble(MetricDescriptor descriptor, double value) {
                accept(descriptor, value);
            }

            private void accept(MetricDescriptor descriptor, Number value) {
                String prefix = descriptor.prefix();
                if (prefix == null || descriptor.metric() == null) {
                    return;
                }
                if (KEPT_OBJECT_PREFIXES.contains(prefix) && "name".equals(descriptor.discriminator())
                        && descriptor.tagCount() == 0) {
                    objects.computeIfAbsent(prefix, p -> new HashMap<>())
                            .computeIfAbsent(descriptor.discriminatorValue(), n -> new HashMap<>())
                            .put(descriptor.metric(), value);
                } else if (KEPT_GLOBAL_PREFIXES.contains(prefix) && descriptor.discriminator() == null) {
                    global.put(prefix + "." + descriptor.metric(), value);
                }
            }
        });
        return new MemberMetrics(member.getUuid(), address, timestamp, objects, global);
    }

    private record Cached(ClusterMetrics metrics, long createdAt) {

        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - createdAt > ttlMillis;
        }
    }
}
