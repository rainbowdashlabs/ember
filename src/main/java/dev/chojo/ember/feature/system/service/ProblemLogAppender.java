/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom Logback appender that captures ERROR and WARN log events in memory.
 * Aggregates entries by stacktrace/exception to count occurrences.
 * Entries older than 3 days are automatically pruned.
 */
public class ProblemLogAppender extends AppenderBase<ILoggingEvent> {
    private static final long RETENTION_MS = 3L * 24 * 60 * 60 * 1000; // 3 days
    private static final int MAX_ENTRIES = 1000;

    private static ProblemLogAppender instance;

    private final Map<String, ProblemEntry> problems = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    public ProblemLogAppender() {
        instance = this;
    }

    public static ProblemLogAppender instance() {
        return instance;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLevel().toInt() < Level.WARN.toInt()) return;

        pruneOldEntries();

        String key = buildAggregationKey(event);
        problems.compute(key, (k, existing) -> {
            if (existing != null && !existing.acknowledged()) {
                existing.addOccurrence(event.getFormattedMessage(), Instant.ofEpochMilli(event.getTimeStamp()));
                return existing;
            }
            return new ProblemEntry(
                    idCounter.incrementAndGet(),
                    event.getLevel().toString(),
                    event.getLoggerName(),
                    event.getFormattedMessage(),
                    extractStacktrace(event.getThrowableProxy()),
                    extractExceptionClass(event.getThrowableProxy()),
                    extractExceptionMessage(event.getThrowableProxy()),
                    Instant.ofEpochMilli(event.getTimeStamp()));
        });
    }

    private String buildAggregationKey(ILoggingEvent event) {
        var tp = event.getThrowableProxy();
        if (tp != null) {
            return event.getLoggerName() + ":" + tp.getClassName() + ":" + extractFirstFrame(tp);
        }
        return event.getLoggerName() + ":" + event.getLevel() + ":" + event.getFormattedMessage();
    }

    private String extractFirstFrame(IThrowableProxy tp) {
        if (tp.getStackTraceElementProxyArray() != null && tp.getStackTraceElementProxyArray().length > 0) {
            return tp.getStackTraceElementProxyArray()[0].getSTEAsString();
        }
        return "";
    }

    private String extractStacktrace(IThrowableProxy tp) {
        if (tp == null) return null;
        var sb = new StringBuilder();
        sb.append(tp.getClassName()).append(": ").append(tp.getMessage()).append("\n");
        if (tp.getStackTraceElementProxyArray() != null) {
            for (StackTraceElementProxy step : tp.getStackTraceElementProxyArray()) {
                sb.append("    at ").append(step.getSTEAsString()).append("\n");
            }
        }
        if (tp.getCause() != null) {
            sb.append("Caused by: ").append(extractStacktrace(tp.getCause()));
        }
        return sb.toString();
    }

    private String extractExceptionClass(IThrowableProxy tp) {
        return tp != null ? tp.getClassName() : null;
    }

    private String extractExceptionMessage(IThrowableProxy tp) {
        return tp != null ? tp.getMessage() : null;
    }

    private void pruneOldEntries() {
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        problems.entrySet().removeIf(e -> e.getValue().lastOccurrence().toEpochMilli() < cutoff);

        // Cap at MAX_ENTRIES
        if (problems.size() > MAX_ENTRIES) {
            var sorted = new ArrayList<>(problems.entrySet());
            sorted.sort(
                    Comparator.comparingLong(a -> a.getValue().lastOccurrence().toEpochMilli()));
            int toRemove = sorted.size() - MAX_ENTRIES;
            for (int i = 0; i < toRemove; i++) {
                problems.remove(sorted.get(i).getKey());
            }
        }
    }

    public List<ProblemEntry> getProblems(boolean includeAcknowledged) {
        pruneOldEntries();
        return problems.values().stream()
                .filter(p -> includeAcknowledged || !p.acknowledged())
                .sorted((a, b) -> b.lastOccurrence().compareTo(a.lastOccurrence()))
                .toList();
    }

    public boolean acknowledge(long id) {
        for (var entry : problems.values()) {
            if (entry.id() == id) {
                entry.setAcknowledged(true);
                return true;
            }
        }
        return false;
    }

    public int acknowledgeAll() {
        int count = 0;
        for (var entry : problems.values()) {
            if (!entry.acknowledged()) {
                entry.setAcknowledged(true);
                count++;
            }
        }
        return count;
    }

    /**
     * Represents an aggregated problem with occurrence count and optional distinct messages.
     */
    public static class ProblemEntry {
        private final long id;
        private final String level;
        private final String logger;
        private final List<String> distinctMessages = Collections.synchronizedList(new ArrayList<>());
        private volatile Instant lastOccurrence;
        private volatile int count;
        private volatile boolean acknowledged;

        ProblemEntry(
                long id,
                String level,
                String logger,
                String message,
                String stacktrace,
                String exceptionClass,
                String exceptionMessage,
                Instant timestamp) {
            this.id = id;
            this.level = level;
            this.logger = logger;
            this.lastOccurrence = timestamp;
            this.count = 1;
            this.distinctMessages.add(message);
        }

        synchronized void addOccurrence(String message, Instant timestamp) {
            count++;
            if (timestamp.isAfter(lastOccurrence)) {
                lastOccurrence = timestamp;
            }
            if (distinctMessages.size() < 50 && !distinctMessages.contains(message)) {
                distinctMessages.add(message);
            }
        }

        void setAcknowledged(boolean ack) {
            this.acknowledged = ack;
        }

        public long id() {
            return id;
        }

        public String level() {
            return level;
        }

        public String logger() {
            return logger;
        }

        public Instant lastOccurrence() {
            return lastOccurrence;
        }

        public int count() {
            return count;
        }

        public boolean acknowledged() {
            return acknowledged;
        }
    }
}
