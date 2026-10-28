package io.github.noproblem2000.tsarevnacache.structures;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.codec.ListSubCodec;
import com.hazelcast.client.impl.protocol.codec.MultiMapEntrySetCodec;
import com.hazelcast.client.impl.protocol.codec.QueueIteratorCodec;
import com.hazelcast.client.impl.protocol.codec.ReplicatedMapEntrySetCodec;
import com.hazelcast.client.impl.protocol.codec.SetGetAllCodec;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.multimap.MultiMap;
import com.hazelcast.nio.serialization.HazelcastSerializationException;
import com.hazelcast.replicatedmap.ReplicatedMap;
import io.github.noproblem2000.tsarevnacache.api.dto.OtherStructureDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.OtherStructureDTO.Item;
import io.github.noproblem2000.tsarevnacache.api.dto.StructureType;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.config.properties.ExplorerProperties;
import io.github.noproblem2000.tsarevnacache.hazelcast.ClientAccess;
import io.github.noproblem2000.tsarevnacache.inspector.DataRefs;
import io.github.noproblem2000.tsarevnacache.inspector.HazelcastValueInspectorService;
import io.github.noproblem2000.tsarevnacache.metrics.ClusterMetricsService;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Basic view and maintenance of IQueue, ISet, IList, MultiMap and ReplicatedMap.
 *
 * <p>Items are read with raw protocol calls and inspected one by one, so undecodable items do not
 * break the view. Except for lists (which support ranged reads), clients can only fetch these
 * structures as a whole; structures larger than {@code admin.explorer.max-fetch-items} therefore
 * report their size only.
 */
@Service
public class OtherStructuresService {

    private static final Set<StructureType> SUPPORTED = EnumSet.of(StructureType.QUEUE, StructureType.SET,
            StructureType.LIST, StructureType.MULTI_MAP, StructureType.REPLICATED_MAP);

    private final ClientAccess clientAccess;
    private final StructureRegistryService registry;
    private final HazelcastValueInspectorService inspector;
    private final ClusterMetricsService metricsService;
    private final ExplorerProperties properties;

    public OtherStructuresService(ClientAccess clientAccess, StructureRegistryService registry,
                                  HazelcastValueInspectorService inspector, ClusterMetricsService metricsService,
                                  ExplorerProperties properties) {
        this.clientAccess = clientAccess;
        this.registry = registry;
        this.inspector = inspector;
        this.metricsService = metricsService;
        this.properties = properties;
    }

    /** Resolves and validates the {@code {type}} URL segment. */
    public StructureType parseType(String value) {
        return StructureType.fromPath(value)
                .filter(SUPPORTED::contains)
                .orElseThrow(() -> new ApiException(ErrorCode.UNSUPPORTED_STRUCTURE_TYPE,
                        "Unsupported structure type '" + value + "'",
                        Map.of("supported", SUPPORTED.stream().map(StructureType::pathName).toList())));
    }

    public OtherStructureDTO view(StructureType type, String name, int requestedLimit) {
        registry.requireExists(type, name);
        int limit = Math.max(1, Math.min(requestedLimit, properties.getMaxPreviewItems()));
        HazelcastInstance hz = clientAccess.hazelcast();
        return switch (type) {
            case QUEUE -> {
                long size = hz.getQueue(name).size();
                yield values(type, name, size, limit, () -> QueueIteratorCodec.decodeResponse(
                        clientAccess.invokeOnPartition(QueueIteratorCodec.encodeRequest(name), name,
                                partitionOf(hz.getQueue(name).getPartitionKey()))));
            }
            case SET -> {
                long size = hz.getSet(name).size();
                yield values(type, name, size, limit, () -> SetGetAllCodec.decodeResponse(
                        clientAccess.invokeOnPartition(SetGetAllCodec.encodeRequest(name), name,
                                partitionOf(hz.getSet(name).getPartitionKey()))));
            }
            case LIST -> {
                long size = hz.getList(name).size();
                List<Data> items;
                try {
                    items = listHead(name, (int) Math.min(size, limit));
                } catch (IndexOutOfBoundsException shrunk) {
                    // the list shrank between size() and the ranged read
                    size = hz.getList(name).size();
                    items = listHead(name, (int) Math.min(size, limit));
                }
                yield new OtherStructureDTO(type, name, size, limit, true, toValueItems(items, limit));
            }
            case MULTI_MAP -> {
                long size = hz.getMultiMap(name).size();
                if (size > properties.getMaxFetchItems()) {
                    yield new OtherStructureDTO(type, name, size, limit, false, List.of());
                }
                ClientMessage response = clientAccess.invokeOnRandomTarget(MultiMapEntrySetCodec.encodeRequest(name), name);
                yield new OtherStructureDTO(type, name, size, limit, true,
                        toEntryItems(MultiMapEntrySetCodec.decodeResponse(response), limit, true));
            }
            case REPLICATED_MAP -> {
                long size = hz.getReplicatedMap(name).size();
                if (size > properties.getMaxFetchItems()) {
                    yield new OtherStructureDTO(type, name, size, limit, false, List.of());
                }
                // replicated map reads are partition bound; any partition sees the member's full copy
                ClientMessage response = clientAccess.invokeOnPartition(
                        ReplicatedMapEntrySetCodec.encodeRequest(name), name, partitionOf(name));
                yield new OtherStructureDTO(type, name, size, limit, true,
                        toEntryItems(ReplicatedMapEntrySetCodec.decodeResponse(response), limit, false));
            }
            default -> throw parseTypeError(type);
        };
    }

    /**
     * Removes one item. Queues, sets and lists need {@code ref}; multimaps need {@code keyRef}
     * and optionally {@code ref} (one value, otherwise all values of the key); replicated maps
     * need {@code keyRef}.
     */
    public void removeItem(StructureType type, String name, String ref, String keyRef) {
        registry.requireExists(type, name);
        HazelcastInstance hz = clientAccess.hazelcast();
        boolean removed = switch (type) {
            case QUEUE -> hz.getQueue(name).remove(requireRef(ref, "ref"));
            case SET -> hz.getSet(name).remove(requireRef(ref, "ref"));
            case LIST -> hz.getList(name).remove((Object) requireRef(ref, "ref"));
            case MULTI_MAP -> {
                MultiMap<Object, Object> multiMap = hz.getMultiMap(name);
                Data key = requireRef(keyRef, "keyRef");
                if (StringUtils.hasText(ref)) {
                    yield multiMap.remove(key, DataRefs.decode(ref));
                }
                if (!multiMap.containsKey(key)) {
                    yield false;
                }
                multiMap.delete(key);
                yield true;
            }
            case REPLICATED_MAP -> {
                ReplicatedMap<Object, Object> replicatedMap = hz.getReplicatedMap(name);
                Data key = requireRef(keyRef, "keyRef");
                if (!replicatedMap.containsKey(key)) {
                    yield false;
                }
                try {
                    replicatedMap.remove(key);
                } catch (HazelcastSerializationException ignored) {
                    // the entry is removed; only the returned old value could not be decoded
                }
                yield true;
            }
            default -> throw parseTypeError(type);
        };
        if (!removed) {
            throw new ApiException(ErrorCode.ITEM_NOT_FOUND, "Item not found in " + type + " '" + name + "'",
                    Map.of("type", type.name(), "name", name));
        }
        metricsService.invalidate();
    }

    /** Clears the structure. The caller must repeat the structure name as confirmation. */
    public void clear(StructureType type, String name, String confirmation) {
        if (!name.equals(confirmation)) {
            throw new ApiException(ErrorCode.CONFIRMATION_REQUIRED,
                    "Repeat the structure name in the 'confirm' parameter to clear it", Map.of("name", name));
        }
        registry.requireExists(type, name);
        HazelcastInstance hz = clientAccess.hazelcast();
        switch (type) {
            case QUEUE -> hz.getQueue(name).clear();
            case SET -> hz.getSet(name).clear();
            case LIST -> hz.getList(name).clear();
            case MULTI_MAP -> hz.getMultiMap(name).clear();
            case REPLICATED_MAP -> hz.getReplicatedMap(name).clear();
            default -> throw parseTypeError(type);
        }
        metricsService.invalidate();
    }

    private OtherStructureDTO values(StructureType type, String name, long size, int limit,
                                     Supplier<List<Data>> fetch) {
        if (size > properties.getMaxFetchItems()) {
            return new OtherStructureDTO(type, name, size, limit, false, List.of());
        }
        return new OtherStructureDTO(type, name, size, limit, true, toValueItems(fetch.get(), limit));
    }

    private List<Item> toValueItems(List<Data> items, int limit) {
        return items.stream()
                .limit(limit)
                .map(data -> new Item(DataRefs.encode(data), inspector.inspect(data), null, null))
                .toList();
    }

    private List<Item> toEntryItems(List<Map.Entry<Data, Data>> entries, int limit, boolean valueRefs) {
        return entries.stream()
                .limit(limit)
                .map(entry -> new Item(
                        valueRefs ? DataRefs.encode(entry.getValue()) : null,
                        inspector.inspect(entry.getValue()),
                        DataRefs.encode(entry.getKey()),
                        inspector.inspect(entry.getKey())))
                .toList();
    }

    private List<Data> listHead(String name, int count) {
        if (count <= 0) {
            return List.of();
        }
        return ListSubCodec.decodeResponse(clientAccess.invokeOnPartition(ListSubCodec.encodeRequest(name, 0, count),
                name, partitionOf(clientAccess.hazelcast().getList(name).getPartitionKey())));
    }

    private int partitionOf(String partitionKey) {
        return clientAccess.partitionIdOf(partitionKey);
    }

    private static Data requireRef(String ref, String parameter) {
        if (!StringUtils.hasText(ref)) {
            throw new ApiException(ErrorCode.VALUE_REQUIRED, "Parameter '" + parameter + "' is required",
                    Map.of("parameter", parameter));
        }
        return DataRefs.decode(ref);
    }

    private static ApiException parseTypeError(StructureType type) {
        return new ApiException(ErrorCode.UNSUPPORTED_STRUCTURE_TYPE, "Unsupported structure type " + type);
    }
}
