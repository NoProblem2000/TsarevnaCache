package io.github.noproblem2000.tsarevnacache.maps;

import io.github.noproblem2000.tsarevnacache.api.dto.DataType;

/**
 * Identifies a single map key: either a serialized {@code keyRef} or a typed textual key.
 *
 * @param keyRef    serialized key reference (wins when present)
 * @param key       textual key
 * @param keyType   key type, {@link DataType#STRING} when omitted
 * @param className class for {@link DataType#CLASS} keys
 */
public record KeySpec(String keyRef, String key, DataType keyType, String className) {
}
