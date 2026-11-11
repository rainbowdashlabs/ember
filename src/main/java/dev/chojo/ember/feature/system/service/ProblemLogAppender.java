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
import dev.chojo.ember.feature.system.entity.ProblemLevel;
import dev.chojo.ember.util.ProblemKey;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Gathers every warning and error for the problem log in the database: what happened since the last
 * write, per fingerprint, which {@link ProblemLogFlusher} takes and writes. It never touches the database
 * itself, so a log call never waits for one, and it holds at most {@link #PENDING_LIMIT} problems while
 * the database cannot be written to; further problems, and problems put back that no longer fit, are
 * counted on one overflow entry, refusals included: the overflow is a fault in its own right. Whatever
 * is logged on the flusher's own thread while it writes is not gathered, so a database that refuses the
 * write does not keep feeding the log it refuses; what other threads log about the same outage is a real
 * problem and is gathered like any other.
 */
public class ProblemLogAppender extends AppenderBase<ILoggingEvent> {
    /** How many problems wait for the database at most; any further one is counted on the overflow entry. */
    static final int PENDING_LIMIT = 1000;

    /** The fingerprint of the entry counting the problems that did not fit while the database was away. */
    static final String OVERFLOW_FINGERPRINT = "problem-log-overflow";

    /** Set on the flusher's thread while it writes, so nothing logged there is gathered again. */
    private static final ThreadLocal<Boolean> WRITING = ThreadLocal.withInitial(() -> false);

    private static ProblemLogAppender instance;

    private final Map<String, PendingProblem> pending = new ConcurrentHashMap<>();

    public ProblemLogAppender() {
        this(true);
    }

    /**
     * An appender that registers itself as the one the application reads, or one that stays private to
     * whoever made it.
     *
     * @param register whether it becomes {@link #instance()}
     */
    ProblemLogAppender(boolean register) {
        if (register) instance = this;
    }

    public static ProblemLogAppender instance() {
        return instance;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLevel().toInt() < Level.WARN.toInt()) return;
        if (WRITING.get()) return;

        gather(buildAggregationKey(event), event);
    }

    /**
     * Counts the event on what waits for the database, or on the overflow entry once too many different
     * problems wait.
     */
    private void gather(String key, ILoggingEvent event) {
        String fingerprint = fingerprintOf(key);
        Instant at = Instant.ofEpochMilli(event.getTimeStamp());
        String message = event.getFormattedMessage() == null ? "" : event.getFormattedMessage();
        IThrowableProxy throwable = event.getThrowableProxy();
        if (!pending.containsKey(fingerprint) && pending.size() >= PENDING_LIMIT) {
            pending.compute(OVERFLOW_FINGERPRINT, (_, overflow) -> {
                var target = overflow == null ? overflowEntry(at) : overflow;
                target.add(event.getLoggerName() + ": " + message, null, () -> null, at, event.getThreadName());
                return target;
            });
            return;
        }
        pending.compute(fingerprint, (_, existing) -> {
            var target = existing == null
                    ? new PendingProblem(
                            fingerprint,
                            levelOf(event),
                            event.getLoggerName(),
                            message,
                            extractExceptionClass(throwable),
                            at)
                    : existing;
            target.add(
                    message,
                    extractExceptionMessage(throwable),
                    () -> extractStacktrace(throwable),
                    at,
                    event.getThreadName());
            return target;
        });
    }

    private static PendingProblem overflowEntry(Instant at) {
        return new PendingProblem(
                OVERFLOW_FINGERPRINT,
                ProblemLevel.WARN,
                ProblemLogAppender.class.getName(),
                "More different problems than the problem log holds while it cannot write; the rest are counted here",
                null,
                at);
    }

    /**
     * Takes everything gathered for the database, leaving nothing behind; what is logged meanwhile is
     * gathered anew.
     *
     * @return the problems gathered since the last call
     */
    List<PendingProblem> drainPending() {
        var drained = new ArrayList<PendingProblem>();
        for (String fingerprint : List.copyOf(pending.keySet())) {
            var problem = pending.remove(fingerprint);
            if (problem != null) drained.add(problem);
        }
        return drained;
    }

    /**
     * Puts back what could not be written, adding what was gathered for the same problem meanwhile.
     *
     * @param problem the problem that was not written
     */
    void restore(PendingProblem problem) {
        boolean overflow = problem.fingerprint().equals(OVERFLOW_FINGERPRINT);
        if (!overflow && !pending.containsKey(problem.fingerprint()) && pending.size() >= PENDING_LIMIT) {
            var record = problem.record();
            pending.compute(OVERFLOW_FINGERPRINT, (_, existing) -> {
                var target = existing == null ? overflowEntry(record.firstSeen()) : existing;
                target.countIn(record);
                return target;
            });
            return;
        }
        pending.merge(problem.fingerprint(), problem, (current, earlier) -> {
            current.absorb(earlier);
            return current;
        });
    }

    /**
     * Runs a write with everything logged on this thread meanwhile left ungathered.
     *
     * @param write the write
     * @return what the write returned
     */
    static <T> T writing(Supplier<T> write) {
        WRITING.set(true);
        try {
            return write.get();
        } finally {
            WRITING.set(false);
        }
    }

    /** The stored form of an aggregation key: short, fixed in length, and free of what the key quotes. */
    static String fingerprintOf(String key) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    /** The level an event is kept at: a refusal where its caller marked it so, otherwise its own. */
    static ProblemLevel levelOf(ILoggingEvent event) {
        var markers = event.getMarkerList();
        if (markers != null && markers.contains(ProblemKey.REFUSAL)) return ProblemLevel.REFUSAL;
        return ProblemLevel.valueOf(event.getLevel().toString());
    }

    /** The problem an event names itself, where its caller added {@link ProblemKey#NAME}. */
    private static @Nullable String declaredKey(ILoggingEvent event) {
        var pairs = event.getKeyValuePairs();
        if (pairs == null) return null;
        for (var pair : pairs) {
            if (ProblemKey.NAME.equals(pair.key) && pair.value != null) return pair.value.toString();
        }
        return null;
    }

    private String buildAggregationKey(ILoggingEvent event) {
        String declared = declaredKey(event);
        if (declared != null) return event.getLoggerName() + ":" + levelOf(event) + ":" + declared;
        var tp = event.getThrowableProxy();
        if (tp != null) {
            return event.getLoggerName() + ":" + levelOf(event) + ":" + tp.getClassName() + ":" + extractFirstFrame(tp);
        }
        return event.getLoggerName() + ":" + levelOf(event) + ":" + event.getFormattedMessage();
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
                sb.append("    ").append(step.getSTEAsString()).append("\n");
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
}
