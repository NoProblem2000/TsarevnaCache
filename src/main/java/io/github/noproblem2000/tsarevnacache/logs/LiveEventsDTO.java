package io.github.noproblem2000.tsarevnacache.logs;

import java.util.List;

/**
 * Events of the live stream.
 *
 * @param events   matching events, oldest first
 * @param lastSeq  sequence number of the newest event in the buffer (matching or not); pass it as
 *                 {@code after} to receive only newer events
 * @param buffered events currently kept by the buffer
 * @param capacity buffer size ({@code admin.logs.live-buffer-size})
 * @param dropped  events dropped so far because the buffer was full
 */
public record LiveEventsDTO(List<LiveEventDTO> events, long lastSeq, int buffered, int capacity, long dropped) {
}
