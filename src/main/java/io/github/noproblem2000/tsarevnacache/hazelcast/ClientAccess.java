package io.github.noproblem2000.tsarevnacache.hazelcast;

import com.hazelcast.client.impl.clientside.HazelcastClientInstanceImpl;
import com.hazelcast.client.impl.clientside.HazelcastClientProxy;
import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.spi.impl.ClientInvocation;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.internal.serialization.Data;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.config.properties.ExplorerProperties;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * Low level access to the Hazelcast client: raw protocol invocations and connection state.
 *
 * <p>Raw invocations exchange serialized {@link Data} without deserializing it in the client
 * proxies, which is what allows the explorer to page through entries whose classes are unknown.
 * This relies on the client's internal API and is therefore pinned to Hazelcast 5.5.
 */
@Component
public class ClientAccess {

    private final HazelcastInstance hazelcast;
    private final ExplorerProperties properties;

    public ClientAccess(HazelcastInstance hazelcastClient, ExplorerProperties properties) {
        this.hazelcast = hazelcastClient;
        this.properties = properties;
    }

    public HazelcastInstance hazelcast() {
        return hazelcast;
    }

    /** Whether the client is running and holds at least one cluster connection. */
    public boolean isConnected() {
        return hazelcast.getLifecycleService().isRunning()
                && !client().getConnectionManager().getActiveConnections().isEmpty();
    }

    /**
     * @throws ApiException with {@link ErrorCode#CLUSTER_UNAVAILABLE} when not connected
     */
    public void ensureConnected() {
        if (!isConnected()) {
            throw new ApiException(ErrorCode.CLUSTER_UNAVAILABLE, "The admin client is not connected to the cluster");
        }
    }

    public int partitionCount() {
        return client().getClientPartitionService().getPartitionCount();
    }

    public int partitionId(Data key) {
        return client().getClientPartitionService().getPartitionId(key);
    }

    public int partitionIdOf(Object partitionKey) {
        return client().getClientPartitionService().getPartitionId(partitionKey);
    }

    public CompletableFuture<ClientMessage> invokeOnPartitionAsync(ClientMessage request, String objectName,
                                                                   int partitionId) {
        return new ClientInvocation(client(), request, objectName, partitionId).invoke();
    }

    public ClientMessage invokeOnPartition(ClientMessage request, String objectName, int partitionId) {
        return await(invokeOnPartitionAsync(request, objectName, partitionId));
    }

    /** Invokes on any member (used for cluster wide operations such as paging queries). */
    public ClientMessage invokeOnRandomTarget(ClientMessage request, String objectName) {
        return await(new ClientInvocation(client(), request, objectName).invoke());
    }

    public CompletableFuture<ClientMessage> invokeOnMemberAsync(ClientMessage request, UUID memberUuid) {
        return new ClientInvocation(client(), request, null, memberUuid).invoke();
    }

    /**
     * Waits for an invocation with the configured operation timeout and unwraps failures.
     */
    public <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(properties.getOperationTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.CLUSTER_TIMEOUT, "Interrupted while waiting for the cluster", Map.of(), e);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ApiException(ErrorCode.CLUSTER_TIMEOUT, "The cluster did not answer within "
                    + properties.getOperationTimeout().toSeconds() + "s", Map.of(), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }

    private HazelcastClientInstanceImpl client() {
        if (!(hazelcast instanceof HazelcastClientProxy proxy)) {
            throw new IllegalStateException("A Hazelcast client instance is required, got " + hazelcast.getClass().getName());
        }
        return proxy.client;
    }
}
