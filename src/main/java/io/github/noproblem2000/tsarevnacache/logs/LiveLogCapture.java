package io.github.noproblem2000.tsarevnacache.logs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import io.github.noproblem2000.tsarevnacache.TsarevnaCacheApplication;
import io.github.noproblem2000.tsarevnacache.config.properties.LogViewerProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Copies log events into the live stream: a Logback appender attached to the {@code com.hazelcast}
 * logger (client connection, partition and serialization messages) and to the admin service's own
 * package (warnings and errors, e.g. values that cannot be deserialized). The minimum levels are
 * {@code admin.logs.live-hazelcast-level} and {@code admin.logs.live-application-level}.
 */
@Slf4j
@Component
public class LiveLogCapture {

    static final String HAZELCAST_LOGGER = "com.hazelcast";
    static final String APPLICATION_LOGGER = TsarevnaCacheApplication.class.getPackageName();

    private final LiveEventBuffer buffer;
    private final Level hazelcastLevel;
    private final Level applicationLevel;
    private final List<Logger> attachedTo = new ArrayList<>();
    private BufferAppender appender;

    public LiveLogCapture(LiveEventBuffer buffer, LogViewerProperties properties) {
        this.buffer = buffer;
        this.hazelcastLevel = Level.toLevel(properties.getLiveHazelcastLevel(), Level.INFO);
        this.applicationLevel = Level.toLevel(properties.getLiveApplicationLevel(), Level.WARN);
    }

    @PostConstruct
    void attach() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext context)) {
            log.info("Log events are not copied into the live stream: Logback is not the logging backend");
            return;
        }
        appender = new BufferAppender(this);
        appender.setContext(context);
        appender.setName("tsarevna-live-stream-" + System.identityHashCode(this));
        appender.start();
        for (String name : List.of(HAZELCAST_LOGGER, APPLICATION_LOGGER)) {
            Logger logger = context.getLogger(name);
            logger.addAppender(appender);
            attachedTo.add(logger);
        }
        buffer.add(LogLevel.INFO, LiveEventSource.LOG, LiveLogCapture.class.getName(),
                "Live stream started: capturing " + HAZELCAST_LOGGER + " (" + hazelcastLevel + "+) and "
                        + APPLICATION_LOGGER + " (" + applicationLevel + "+) log events");
    }

    @PreDestroy
    void detach() {
        for (Logger logger : attachedTo) {
            logger.detachAppender(appender);
        }
        attachedTo.clear();
        if (appender != null) {
            appender.stop();
        }
    }

    void append(ILoggingEvent event) {
        Level threshold = event.getLoggerName().startsWith(HAZELCAST_LOGGER) ? hazelcastLevel : applicationLevel;
        if (!event.getLevel().isGreaterOrEqual(threshold)) {
            return;
        }
        IThrowableProxy throwable = event.getThrowableProxy();
        buffer.add(event.getTimeStamp(), toLevel(event.getLevel()), LiveEventSource.LOG, event.getLoggerName(),
                event.getThreadName(), event.getFormattedMessage(),
                throwable == null ? null : ThrowableProxyUtil.asString(throwable));
    }

    private static LogLevel toLevel(Level level) {
        if (level.isGreaterOrEqual(Level.ERROR)) {
            return LogLevel.ERROR;
        }
        if (level.isGreaterOrEqual(Level.WARN)) {
            return LogLevel.WARN;
        }
        return level.isGreaterOrEqual(Level.INFO) ? LogLevel.INFO : LogLevel.DEBUG;
    }

    /** The appender itself; it must never log, it runs inside the logging call of other code. */
    private static final class BufferAppender extends AppenderBase<ILoggingEvent> {

        private final LiveLogCapture capture;

        BufferAppender(LiveLogCapture capture) {
            this.capture = capture;
        }

        @Override
        protected void append(ILoggingEvent event) {
            try {
                capture.append(event);
            } catch (RuntimeException ignored) {
                // a broken event must not break the caller's logging
            }
        }
    }
}
