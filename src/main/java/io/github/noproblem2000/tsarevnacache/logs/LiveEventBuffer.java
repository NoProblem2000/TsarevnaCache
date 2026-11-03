package io.github.noproblem2000.tsarevnacache.logs;

import io.github.noproblem2000.tsarevnacache.config.properties.LogViewerProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Thread-safe ring buffer of the live stream: the latest {@code admin.logs.live-buffer-size}
 * cluster events and log events, oldest dropped first. Writers never block and never log.
 */
@Component
public class LiveEventBuffer {

    /** Stack traces are cut to this many characters. */
    static final int MAX_STACK_TRACE = 16_000;

    /** Messages are cut to this many characters. */
    static final int MAX_MESSAGE = 8_000;

    private final ConcurrentLinkedDeque<LiveEventDTO> events = new ConcurrentLinkedDeque<>();
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final int capacity;

    public LiveEventBuffer(LogViewerProperties properties) {
        this.capacity = properties.getLiveBufferSize();
    }

    /** Adds an event stamped with the current time. */
    public void add(LogLevel level, LiveEventSource source, String logger, String message) {
        add(System.currentTimeMillis(), level, source, logger, Thread.currentThread().getName(), message, null);
    }

    public void add(long timestamp, LogLevel level, LiveEventSource source, String logger, String thread,
                    String message, String stackTrace) {
        String text = cut(message, MAX_MESSAGE);
        String trace = cut(stackTrace, MAX_STACK_TRACE);
        // sequence numbers and deque order must agree: readers stop at the first event already seen
        synchronized (events) {
            events.addLast(new LiveEventDTO(sequence.incrementAndGet(), timestamp, level, source, logger, thread,
                    text, trace));
        }
        if (size.incrementAndGet() > capacity) {
            if (events.pollFirst() != null) {
                size.decrementAndGet();
                dropped.incrementAndGet();
            }
        }
    }

    /**
     * Events newer than {@code after} that pass the filters, oldest first; at most {@code limit}
     * (the newest ones are kept).
     */
    public LiveEventsDTO events(long after, LogLevelFilter level, String search, int limit) {
        LogLevelFilter filter = level == null ? LogLevelFilter.ALL : level;
        String needle = StringUtils.hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;
        long last = sequence.get();
        List<LiveEventDTO> matching = new ArrayList<>();
        Iterator<LiveEventDTO> newestFirst = events.descendingIterator();
        while (newestFirst.hasNext() && matching.size() < limit) {
            LiveEventDTO event = newestFirst.next();
            if (event.seq() > last) {
                continue;
            }
            if (event.seq() <= after) {
                break;
            }
            if (filter.accepts(event.level()) && (needle == null || matches(event, needle))) {
                matching.add(event);
            }
        }
        Collections.reverse(matching);
        return new LiveEventsDTO(matching, last, size.get(), capacity, dropped.get());
    }

    /** Removes every event; the sequence keeps counting. */
    public void clear() {
        while (events.pollFirst() != null) {
            size.decrementAndGet();
        }
    }

    public int capacity() {
        return capacity;
    }

    private static boolean matches(LiveEventDTO event, String needle) {
        return contains(event.message(), needle) || contains(event.logger(), needle)
                || contains(event.thread(), needle) || contains(event.stackTrace(), needle)
                || event.source().name().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max) + " …";
    }
}
