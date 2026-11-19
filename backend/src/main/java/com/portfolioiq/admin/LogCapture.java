package com.portfolioiq.admin;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Keeps the most recent warnings and errors in memory for the administrator panel. Messages are redacted and only
 * the exception class name is kept (never the stack trace or its message).
 */
@Component
public class LogCapture {
    private final LogBuffer buffer = new LogBuffer(500);
    private BufferAppender appender;

    public LogBuffer buffer() { return buffer; }

    @PostConstruct
    void start() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof LoggerContext ctx) {
            appender = new BufferAppender(buffer);
            appender.setContext(ctx);
            appender.setName("admin-log-buffer");
            appender.start();
            ctx.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
        }
    }

    @PreDestroy
    void stop() {
        if (appender != null && LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx) {
            ctx.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
            appender.stop();
        }
    }

    private static final class BufferAppender extends AppenderBase<ILoggingEvent> {
        private final LogBuffer buffer;

        BufferAppender(LogBuffer buffer) { this.buffer = buffer; }

        @Override
        protected void append(ILoggingEvent e) {
            if (!e.getLevel().isGreaterOrEqual(Level.WARN)) return;
            String name = e.getLoggerName();
            String shortName = name == null ? "" : name.substring(name.lastIndexOf('.') + 1);
            IThrowableProxy t = e.getThrowableProxy();
            buffer.add(new LogEntry(Instant.ofEpochMilli(e.getTimeStamp()), e.getLevel().toString(), shortName,
                    LogRedactor.redact(e.getFormattedMessage()), t == null ? null : t.getClassName()));
        }
    }
}
