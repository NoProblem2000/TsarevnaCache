package io.github.noproblem2000.tsarevnacache.maps;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.codec.MapFetchKeysCodec;
import com.hazelcast.client.impl.protocol.codec.MapGetEntryViewCodec;
import com.hazelcast.client.impl.protocol.codec.MapKeySetWithPagingPredicateCodec;
import com.hazelcast.client.impl.protocol.codec.holder.PagingPredicateHolder;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.util.IterationType;
import com.hazelcast.internal.util.ThreadUtil;
import com.hazelcast.map.IMap;
import com.hazelcast.map.impl.SimpleEntryView;
import com.hazelcast.query.PagingPredicate;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.Predicates;
import com.hazelcast.query.QueryException;
import com.hazelcast.query.impl.predicates.PagingPredicateImpl;
import io.github.noproblem2000.tsarevnacache.api.dto.ByteSize;
import io.github.noproblem2000.tsarevnacache.api.dto.DataType;
import io.github.noproblem2000.tsarevnacache.api.dto.EntryDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.EntryMetadataDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.EntryPageDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.EntryPageDTO.SearchMode;
import io.github.noproblem2000.tsarevnacache.api.dto.InspectedValue;
import io.github.noproblem2000.tsarevnacache.api.dto.MapStatsDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.PutEntryRequest;
import io.github.noproblem2000.tsarevnacache.api.dto.StructureType;
import io.github.noproblem2000.tsarevnacache.api.dto.TypedValueInput;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.config.properties.ExplorerProperties;
import io.github.noproblem2000.tsarevnacache.hazelcast.ClientAccess;
import io.github.noproblem2000.tsarevnacache.inspector.DataRefs;
import io.github.noproblem2000.tsarevnacache.inspector.HazelcastValueInspectorService;
import io.github.noproblem2000.tsarevnacache.metrics.ClusterMetrics;
import io.github.noproblem2000.tsarevnacache.metrics.ClusterMetricsService;
import io.github.noproblem2000.tsarevnacache.metrics.MemberMetrics;
import io.github.noproblem2000.tsarevnacache.structures.StructureRegistryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * IMap inspection and CRUD.
 *
 * <p>Reads never call {@code entrySet()}/{@code values()} and never deserialize inside the client
 * proxies; they use raw protocol calls and pass every key and value through the
 * {@link HazelcastValueInspectorService}, so one undecodable entry cannot break a page:
 * <ul>
 *   <li><b>Unfiltered:</b> cursor based partition scan ({@code MapFetchKeysCodec}); cost is
 *       proportional to the page size, not to the map size.</li>
 *   <li><b>SQL predicate:</b> {@code Predicates.sql} wrapped in a {@code PagingPredicate}, keys
 *       only, evaluated on the members.</li>
 *   <li><b>Exact key:</b> a single {@code getEntryView}.</li>
 * </ul>
 * Each listed key is then resolved with {@code getEntryView} (in parallel), which returns value
 * and metadata (cost, hits, TTL, ...) without counting as an access.
 */
@Slf4j
@Service
public class MapInspectorService {

    private final ClientAccess clientAccess;
    private final StructureRegistryService registry;
    private final ClusterMetricsService metricsService;
    private final HazelcastValueInspectorService inspector;
    private final ExplorerProperties properties;

    public MapInspectorService(ClientAccess clientAccess, StructureRegistryService registry,
                               ClusterMetricsService metricsService, HazelcastValueInspectorService inspector,
                               ExplorerProperties properties) {
        this.clientAccess = clientAccess;
        this.registry = registry;
        this.metricsService = metricsService;
        this.inspector = inspector;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ statistics

    public MapStatsDTO stats(String mapName) {
        registry.requireExists(StructureType.MAP, mapName);
        ClusterMetrics metrics = metricsService.current();
        String p = ClusterMetrics.MAP;

        List<MapStatsDTO.Member> members = metrics.members().stream()
                .filter(member -> !member.of(p, mapName).isEmpty())
                .map(member -> memberStats(member, mapName))
                .toList();

        MapStatsDTO.NearCache nearCache = null;
        if (metrics.has(ClusterMetrics.MAP_NEAR_CACHE, mapName)) {
            String nc = ClusterMetrics.MAP_NEAR_CACHE;
            long hits = metrics.sum(nc, mapName, "hits");
            long misses = metrics.sum(nc, mapName, "misses");
            nearCache = new MapStatsDTO.NearCache(
                    metrics.sum(nc, mapName, "ownedEntryCount"),
                    metrics.sum(nc, mapName, "ownedEntryMemoryCost"),
                    hits, misses,
                    hits + misses == 0 ? 0 : (double) hits / (hits + misses),
                    metrics.sum(nc, mapName, "evictions"),
                    metrics.sum(nc, mapName, "expirations"),
                    metrics.sum(nc, mapName, "invalidations"));
        }

        long owned = metrics.sum(p, mapName, "ownedEntryMemoryCost");
        long backup = metrics.sum(p, mapName, "backupEntryMemoryCost");
        long heap = metrics.sum(p, mapName, "heapCost");
        Long backupCount = metrics.max(p, mapName, "backupCount");
        return MapStatsDTO.builder()
                .name(mapName)
                .ownedEntryCount(metrics.sum(p, mapName, "ownedEntryCount"))
                .backupEntryCount(metrics.sum(p, mapName, "backupEntryCount"))
                .backupCount(backupCount == null ? 0 : backupCount)
                .ownedEntryMemoryCost(owned)
                .ownedEntryMemoryCostFormatted(ByteSize.format(owned))
                .backupEntryMemoryCost(backup)
                .backupEntryMemoryCostFormatted(ByteSize.format(backup))
                .heapCost(heap)
                .heapCostFormatted(ByteSize.format(heap))
                .merkleTreesCost(metrics.sum(p, mapName, "merkleTreesCost"))
                .hits(metrics.sum(p, mapName, "hits"))
                .getOperationCount(metrics.sum(p, mapName, "getCount"))
                .putOperationCount(metrics.sum(p, mapName, "putCount"))
                .setOperationCount(metrics.sum(p, mapName, "setCount"))
                .removeOperationCount(metrics.sum(p, mapName, "removeCount"))
                .numberOfOtherOperations(metrics.sum(p, mapName, "numberOfOtherOperations"))
                .numberOfEvents(metrics.sum(p, mapName, "numberOfEvents"))
                .evictionCount(metrics.sum(p, mapName, "evictionCount"))
                .expirationCount(metrics.sum(p, mapName, "expirationCount"))
                .lockedEntryCount(metrics.sum(p, mapName, "lockedEntryCount"))
                .dirtyEntryCount(metrics.sum(p, mapName, "dirtyEntryCount"))
                .queryCount(metrics.sum(p, mapName, "queryCount"))
                .indexedQueryCount(metrics.sum(p, mapName, "indexedQueryCount"))
                .totalGetLatency(metrics.sum(p, mapName, "totalGetLatency"))
                .totalPutLatency(metrics.sum(p, mapName, "totalPutLatency"))
                .totalSetLatency(metrics.sum(p, mapName, "totalSetLatency"))
                .totalRemoveLatency(metrics.sum(p, mapName, "totalRemoveLatency"))
                .maxGetLatency(orZero(metrics.max(p, mapName, "totalMaxGetLatency")))
                .maxPutLatency(orZero(metrics.max(p, mapName, "totalMaxPutLatency")))
                .maxSetLatency(orZero(metrics.max(p, mapName, "totalMaxSetLatency")))
                .maxRemoveLatency(orZero(metrics.max(p, mapName, "totalMaxRemoveLatency")))
                .creationTime(metrics.minPositive(p, mapName, "creationTime"))
                .lastAccessTime(metrics.max(p, mapName, "lastAccessTime"))
                .lastUpdateTime(metrics.max(p, mapName, "lastUpdateTime"))
                .nearCache(nearCache)
                .members(members)
                .stats(metrics.status())
                .build();
    }

    private static MapStatsDTO.Member memberStats(MemberMetrics member, String mapName) {
        Map<String, Number> m = member.of(ClusterMetrics.MAP, mapName);
        return new MapStatsDTO.Member(member.uuid().toString(), member.address(),
                value(m, "ownedEntryCount"), value(m, "backupEntryCount"),
                value(m, "ownedEntryMemoryCost"), value(m, "backupEntryMemoryCost"),
                value(m, "hits"), value(m, "getCount"), value(m, "putCount"),
                value(m, "setCount"), value(m, "removeCount"));
    }

    // ------------------------------------------------------------------ reading entries

    public EntryPageDTO entries(String mapName, EntryQuery query) {
        registry.requireExists(StructureType.MAP, mapName);
        int size = Math.max(1, Math.min(query.size(), properties.getMaxPageSize()));
        int page = Math.max(0, query.page());

        if (StringUtils.hasText(query.keyRef()) || query.searchKey() != null) {
            Data key = resolveKey(new KeySpec(query.keyRef(), query.searchKey(), query.keyType(), query.keyClassName()));
            List<EntryDTO> found = loadEntries(mapName, List.of(key), true);
            return new EntryPageDTO(SearchMode.KEY, 0, size, false, null, null, found);
        }
        if (StringUtils.hasText(query.sqlPredicate())) {
            List<Data> keys = predicateKeys(mapName, query.sqlPredicate().trim(), page, size);
            return new EntryPageDTO(SearchMode.PREDICATE, page, size, keys.size() == size, null, null,
                    loadEntries(mapName, keys, false));
        }
        ScanResult scan = scan(mapName, query.cursor(), size);
        long total = clientAccess.hazelcast().getMap(mapName).size();
        String next = scan.next() == null ? null : scan.next().encode();
        return new EntryPageDTO(SearchMode.ALL, page, size, next != null, next, total,
                loadEntries(mapName, scan.keys(), false));
    }

    /**
     * @throws ApiException with {@link ErrorCode#ENTRY_NOT_FOUND} when the key does not exist
     */
    public EntryDTO entry(String mapName, KeySpec keySpec) {
        registry.requireExists(StructureType.MAP, mapName);
        return entry(mapName, resolveKey(keySpec));
    }

    private EntryDTO entry(String mapName, Data key) {
        List<EntryDTO> entries = loadEntries(mapName, List.of(key), true);
        if (entries.isEmpty()) {
            throw new ApiException(ErrorCode.ENTRY_NOT_FOUND, "Entry not found in map '" + mapName + "'",
                    Map.of("map", mapName));
        }
        return entries.get(0);
    }

    private List<Data> predicateKeys(String mapName, String sql, int page, int size) {
        Predicate<Object, Object> predicate;
        try {
            predicate = Predicates.sql(sql);
        } catch (RuntimeException e) {
            throw invalidPredicate(sql, e);
        }
        PagingPredicate<Object, Object> paging = Predicates.pagingPredicate(predicate, size);
        paging.setPage(page);
        ((PagingPredicateImpl<Object, Object>) paging).setIterationType(IterationType.KEY);
        try {
            ClientMessage request = MapKeySetWithPagingPredicateCodec.encodeRequest(mapName,
                    PagingPredicateHolder.of(paging, inspector.serializationService()));
            ClientMessage response = clientAccess.invokeOnRandomTarget(request, mapName);
            return MapKeySetWithPagingPredicateCodec.decodeResponse(response).response;
        } catch (QueryException | IllegalArgumentException e) {
            throw invalidPredicate(sql, e);
        }
    }

    /**
     * Fills one page by scanning partitions in order. Several partitions are queried in parallel;
     * results are only consumed in partition order and never beyond the page size, so the cursor
     * always points exactly behind the last returned key.
     */
    private ScanResult scan(String mapName, String cursorText, int size) {
        int partitions = clientAccess.partitionCount();
        if (partitions <= 0) {
            throw new ApiException(ErrorCode.CLUSTER_UNAVAILABLE, "Partition table is not available yet");
        }
        ScanCursor cursor = StringUtils.hasText(cursorText) ? ScanCursor.decode(cursorText, partitions)
                : ScanCursor.start(0);
        List<Data> keys = new ArrayList<>(size);
        while (cursor != null && keys.size() < size) {
            int need = size - keys.size();
            int window = Math.min(properties.getScanParallelism(), partitions - cursor.partitionId());
            List<CompletableFuture<ClientMessage>> futures = new ArrayList<>(window);
            for (int i = 0; i < window; i++) {
                ScanCursor position = i == 0 ? cursor : ScanCursor.start(cursor.partitionId() + i);
                futures.add(clientAccess.invokeOnPartitionAsync(
                        MapFetchKeysCodec.encodeRequest(mapName, position.pointers(), need),
                        mapName, position.partitionId()));
            }
            ScanCursor next = null;
            boolean windowCompleted = true;
            for (int i = 0; i < window; i++) {
                int partitionId = cursor.partitionId() + i;
                MapFetchKeysCodec.ResponseParameters result = MapFetchKeysCodec.decodeResponse(
                        clientAccess.await(futures.get(i)));
                if (result.keys.size() > size - keys.size()) {
                    // would overflow the page: re-read this partition with an exact batch size
                    next = i == 0 ? cursor : ScanCursor.start(partitionId);
                    windowCompleted = false;
                    break;
                }
                keys.addAll(result.keys);
                if (!ScanCursor.isExhausted(result.iterationPointers)) {
                    next = new ScanCursor(partitionId, List.copyOf(result.iterationPointers));
                    windowCompleted = false;
                    break;
                }
                if (keys.size() == size) {
                    next = partitionId + 1 < partitions ? ScanCursor.start(partitionId + 1) : null;
                    windowCompleted = false;
                    break;
                }
            }
            if (windowCompleted) {
                int following = cursor.partitionId() + window;
                next = following < partitions ? ScanCursor.start(following) : null;
            }
            futures.forEach(future -> future.cancel(false));
            cursor = next;
        }
        return new ScanResult(keys, cursor);
    }

    /**
     * Resolves keys to entries with parallel {@code getEntryView} calls. Keys removed in the
     * meantime are skipped.
     *
     * @param fullValue include the full JSON value (single entry views) or only previews (pages)
     */
    private List<EntryDTO> loadEntries(String mapName, List<Data> keys, boolean fullValue) {
        long threadId = ThreadUtil.getThreadId();
        List<CompletableFuture<ClientMessage>> futures = keys.stream()
                .map(key -> clientAccess.invokeOnPartitionAsync(
                        MapGetEntryViewCodec.encodeRequest(mapName, key, threadId), mapName,
                        clientAccess.partitionId(key)))
                .toList();
        List<EntryDTO> entries = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            MapGetEntryViewCodec.ResponseParameters response =
                    MapGetEntryViewCodec.decodeResponse(clientAccess.await(futures.get(i)));
            SimpleEntryView<Data, Data> view = response.response;
            if (view == null) {
                continue;
            }
            Data keyData = view.getKey() != null ? view.getKey() : keys.get(i);
            InspectedValue key = inspector.inspect(keyData);
            InspectedValue value = inspector.inspect(view.getValue()).withEntryCost(view.getCost());
            entries.add(new EntryDTO(DataRefs.encode(keyData), key, fullValue ? value : value.withoutValue(),
                    EntryMetadataDTO.from(view, response.maxIdle)));
        }
        return entries;
    }

    // ------------------------------------------------------------------ writing

    /**
     * Creates or replaces an entry with {@code IMap.set} (the previous value is not read back, so
     * undecodable old values do not matter). {@code ttlSeconds}: {@code null} keeps the map's
     * default TTL, {@code 0} means "never expire".
     */
    public EntryDTO put(String mapName, PutEntryRequest request) {
        registry.requireExists(StructureType.MAP, mapName);
        Data key = StringUtils.hasText(request.keyRef())
                ? DataRefs.decode(request.keyRef())
                : inspector.toData(requireKeyInput(request.keyInput()));
        Object value = inspector.toObject(request.valueInput());
        IMap<Object, Object> map = clientAccess.hazelcast().getMap(mapName);
        if (request.ttlSeconds() == null) {
            map.set(key, value);
        } else {
            map.set(key, value, request.ttlSeconds(), TimeUnit.SECONDS);
        }
        metricsService.invalidate();
        return entry(mapName, key);
    }

    /**
     * @throws ApiException with {@link ErrorCode#ENTRY_NOT_FOUND} when the key does not exist
     */
    public void delete(String mapName, KeySpec keySpec) {
        registry.requireExists(StructureType.MAP, mapName);
        Data key = resolveKey(keySpec);
        IMap<Object, Object> map = clientAccess.hazelcast().getMap(mapName);
        if (!map.containsKey(key)) {
            throw new ApiException(ErrorCode.ENTRY_NOT_FOUND, "Entry not found in map '" + mapName + "'",
                    Map.of("map", mapName));
        }
        map.delete(key);
        metricsService.invalidate();
    }

    /** {@code IMap.evictAll()}: evicts all entries that are not locked (MapStore is not called). */
    public void evictAll(String mapName) {
        registry.requireExists(StructureType.MAP, mapName);
        clientAccess.hazelcast().getMap(mapName).evictAll();
        metricsService.invalidate();
    }

    /**
     * {@code IMap.clear()}. The caller must repeat the map name as confirmation.
     */
    public void clear(String mapName, String confirmation) {
        if (!mapName.equals(confirmation)) {
            throw new ApiException(ErrorCode.CONFIRMATION_REQUIRED,
                    "Repeat the map name in the 'confirm' parameter to clear it", Map.of("map", mapName));
        }
        registry.requireExists(StructureType.MAP, mapName);
        clientAccess.hazelcast().getMap(mapName).clear();
        metricsService.invalidate();
    }

    // ------------------------------------------------------------------ helpers

    private Data resolveKey(KeySpec spec) {
        if (StringUtils.hasText(spec.keyRef())) {
            return DataRefs.decode(spec.keyRef());
        }
        return inspector.toData(requireKeyInput(TypedValueInput.ofText(spec.key(),
                spec.keyType() == null ? DataType.STRING : spec.keyType(), spec.className())));
    }

    private static TypedValueInput requireKeyInput(TypedValueInput input) {
        if (input.raw() == null || input.raw().isNull() || input.type() == null) {
            throw new ApiException(ErrorCode.VALUE_REQUIRED, "Either keyRef or key and keyType are required");
        }
        return input;
    }

    private static ApiException invalidPredicate(String sql, Exception e) {
        return new ApiException(ErrorCode.INVALID_PREDICATE, "Invalid predicate: " + e.getMessage(),
                Map.of("predicate", sql), e);
    }

    private static long value(Map<String, Number> metrics, String name) {
        Number value = metrics.get(name);
        return value == null ? 0 : value.longValue();
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }

    private record ScanResult(List<Data> keys, ScanCursor next) {
    }
}
