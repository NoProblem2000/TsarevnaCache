package io.github.noproblem2000.tsarevnacache.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.noproblem2000.tsarevnacache.config.properties.AdminSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static AdminSecurityProperties properties(int maxFailures, Duration window) {
        AdminSecurityProperties properties = new AdminSecurityProperties();
        properties.getLoginThrottle().setMaxFailures(maxFailures);
        properties.getLoginThrottle().setWindow(window);
        return properties;
    }

    @Test
    void blocksAfterTheLimitAndUnblocksWhenTheWindowPasses() {
        MutableClock clock = new MutableClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(properties(3, Duration.ofMinutes(5)), clock);

        limiter.recordFailure("10.0.0.1");
        limiter.recordFailure("10.0.0.1");
        assertThat(limiter.retryAfterSeconds("10.0.0.1")).isZero();
        limiter.recordFailure("10.0.0.1");
        assertThat(limiter.retryAfterSeconds("10.0.0.1")).isEqualTo(300);
        assertThat(limiter.retryAfterSeconds("10.0.0.2")).isZero();

        clock.advance(Duration.ofMinutes(4));
        assertThat(limiter.retryAfterSeconds("10.0.0.1")).isEqualTo(60);
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.retryAfterSeconds("10.0.0.1")).isZero();
    }

    @Test
    void successfulLoginResetsTheCounter() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(properties(2, Duration.ofMinutes(5)), new MutableClock());
        limiter.recordFailure("10.0.0.1");
        limiter.recordSuccess("10.0.0.1");
        limiter.recordFailure("10.0.0.1");
        assertThat(limiter.retryAfterSeconds("10.0.0.1")).isZero();
    }
}
