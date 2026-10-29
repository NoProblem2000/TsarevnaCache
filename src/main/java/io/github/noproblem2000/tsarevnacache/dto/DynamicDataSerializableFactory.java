package io.github.noproblem2000.tsarevnacache.dto;

import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link DataSerializableFactory} registered in the Hazelcast client for the project's factory id
 * (and any other configured dynamic factory id).
 *
 * <p>For a class id it returns, in this order:
 * <ol>
 *   <li>a {@link DynamicDtoRecord} when a schema is registered in the {@link DtoSchemaRegistryService};</li>
 *   <li>the object of the real factory from {@code lib/*.jar} (configured in
 *       {@code admin.hazelcast.serialization.data-serializable-factories}), when present;</li>
 *   <li>{@code null}: Hazelcast then fails the read and the inspector reports the missing
 *       factory id / class id ({@code DTO_SCHEMA_MISSING}).</li>
 * </ol>
 */
@Slf4j
public final class DynamicDataSerializableFactory implements DataSerializableFactory {

    private final int factoryId;
    private final DtoSchemaRegistryService registry;
    private final DataSerializableFactory delegate;

    /**
     * @param delegate real factory of the DTO project loaded from {@code lib/}, may be {@code null}
     */
    public DynamicDataSerializableFactory(int factoryId, DtoSchemaRegistryService registry, DataSerializableFactory delegate) {
        this.factoryId = factoryId;
        this.registry = registry;
        this.delegate = delegate;
    }

    @Override
    public IdentifiedDataSerializable create(int typeId) {
        if (registry.find(factoryId, typeId).isPresent()) {
            return new DynamicDtoRecord(factoryId, typeId, registry);
        }
        if (delegate != null) {
            return delegate.create(typeId);
        }
        log.debug("No DTO schema and no delegate factory for factoryId={}, classId={}", factoryId, typeId);
        return null;
    }

    public int factoryId() {
        return factoryId;
    }

    public DataSerializableFactory delegate() {
        return delegate;
    }
}
