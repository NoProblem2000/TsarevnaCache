package io.github.noproblem2000.tsarevnacache.security;

import io.github.noproblem2000.tsarevnacache.config.properties.AdminSecurityProperties;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Counts failed logins per client address within a sliding window and blocks further attempts once
 * the limit is reached (in-memory, per instance).
 */
@Component
public class LoginAttemptLimiter {

    /** Upper bound of tracked addresses; stale ones are purged when it is exceeded. */
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int maxFailures;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptLimiter(AdminSecurityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginAttemptLimiter(AdminSecurityProperties properties, Clock clock) {
        this.maxFailures = properties.getLoginThrottle().getMaxFailures();
        this.windowMillis = properties.getLoginThrottle().getWindow().toMillis();
        this.clock = clock;
    }

    /**
     * @return seconds until the client may try again, or {@code 0} when it is not blocked
     */
    public long retryAfterSeconds(String client) {
        Deque<Long> attempts = failures.get(client);
        if (attempts == null) {
            return 0;
        }
        synchronized (attempts) {
            long now = clock.millis();
            prune(attempts, now);
            if (attempts.size() < maxFailures) {
                return 0;
            }
            long unblockedAt = attempts.peekFirst() + windowMillis;
            return Math.max(1, (unblockedAt - now + 999) / 1000);
        }
    }

    public void recordFailure(String client) {
        if (failures.size() >= MAX_TRACKED_CLIENTS) {
            purgeStale();
        }
        Deque<Long> attempts = failures.computeIfAbsent(client, key -> new ArrayDeque<>());
        synchronized (attempts) {
            long now = clock.millis();
            prune(attempts, now);
            attempts.addLast(now);
            while (attempts.size() > maxFailures) {
                attempts.removeFirst();
            }
        }
    }

    public void recordSuccess(String client) {
        failures.remove(client);
    }

    private void prune(Deque<Long> attempts, long now) {
        while (!attempts.isEmpty() && attempts.peekFirst() <= now - windowMillis) {
            attempts.removeFirst();
        }
    }

    private void purgeStale() {
        long now = clock.millis();
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), now);
                return entry.getValue().isEmpty();
            }
        });
    }
}
