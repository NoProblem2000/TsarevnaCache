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
 *       {@code admin.hazelcast.serialization.data-serializable-factories}), when it knows the id;</li>
 *   <li>otherwise it throws a {@link DtoSchemaMissingException} with exactly this factory id /
 *       class id. Hazelcast calls the factory for every nested {@code writeObject} too, so for a
 *       registered root DTO with an unregistered nested DTO the exception names the nested one.</li>
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
            IdentifiedDataSerializable fromLibrary = delegate.create(typeId);
            if (fromLibrary != null) {
                return fromLibrary;
            }
        }
        log.debug("No DTO schema and no delegate class for factoryId={}, classId={}", factoryId, typeId);
        throw new DtoSchemaMissingException(factoryId, typeId, registry.classNameFor(factoryId, typeId).orElse(null));
    }

    public int factoryId() {
        return factoryId;
    }

    public DataSerializableFactory delegate() {
        return delegate;
    }
}
