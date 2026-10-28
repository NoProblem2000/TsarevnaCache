package io.github.noproblem2000.tsarevnacache.inspector;

import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.serialization.impl.HeapData;
import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import java.util.Base64;
import java.util.Map;

/**
 * Encodes serialized keys as opaque, URL-safe references. Sending a reference back lets the API
 * address the exact stored key, even when its class is not available to the admin service.
 */
public final class DataRefs {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private DataRefs() {
    }

    public static String encode(Data data) {
        return data == null ? null : ENCODER.encodeToString(data.toByteArray());
    }

    /**
     * @throws ApiException with {@link ErrorCode#INVALID_KEY_REF} if the reference is malformed
     */
    public static Data decode(String ref) {
        if (ref == null || ref.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_KEY_REF, "Key reference is empty");
        }
        byte[] bytes;
        try {
            bytes = DECODER.decode(ref.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_KEY_REF, "Key reference is not valid Base64", Map.of(), e);
        }
        if (bytes.length < HeapData.HEAP_DATA_OVERHEAD) {
            throw new ApiException(ErrorCode.INVALID_KEY_REF, "Key reference is too short");
        }
        return new HeapData(bytes);
    }
}
