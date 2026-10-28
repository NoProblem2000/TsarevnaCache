package io.github.noproblem2000.tsarevnacache.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A cluster member.
 *
 * @param uuid         member UUID
 * @param address      {@code host:port}
 * @param host         host
 * @param port         port
 * @param liteMember   whether the member holds no data
 * @param master       whether the member is the oldest (first in the member list)
 * @param version      Hazelcast version of the member
 * @param usedHeap     used JVM heap in bytes (from metrics)
 * @param maxHeap      max JVM heap in bytes (from metrics)
 * @param uptimeMillis JVM uptime (from metrics)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberDTO(
        String uuid,
        String address,
        String host,
        int port,
        boolean liteMember,
        boolean master,
        String version,
        Long usedHeap,
        Long maxHeap,
        Long uptimeMillis) {
}
