/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hands log lines to whoever will write them to the database, without ever waiting for it.
 *
 * <p>Logback builds its appenders long before there is a database to write to, and a request thread
 * must never block on a log line. So this appender only puts lines into a bounded queue and returns;
 * {@link ApplicationLogWriter} drains it once a connection exists. A full queue drops the oldest
 * lines and counts what it dropped, because a log that stalls the application it is logging is worse
 * than a log with a gap in it.
 *
 * <p>Two families of logger are never captured. The persistence layer is excluded because writing a
 * line goes through it, and a line about writing a line would be written again without end. This
 * appender's own writer is excluded for the same reason.
 */
public class DatabaseLogAppender extends AppenderBase<ILoggingEvent> {

    /**
     * How many lines may wait to be written. Roughly a few seconds of a loud instance, which is
     * enough to ride out a slow write without holding anything up.
     */
    private static final int CAPACITY = 10_000;

    /**
     * Loggers whose lines are never captured, because capturing them would describe the act of
     * capturing. Matched by prefix.
     */
    private static final Set<String> EXCLUDED = Set.of(
            "de.chojo.sadu",
            "com.zaxxer.hikari",
            "org.postgresql",
            ApplicationLogWriter.class.getName(),
            DatabaseLogAppender.class.getName());

    private static final BlockingQueue<LogLine> QUEUE = new ArrayBlockingQueue<>(CAPACITY);
    private static final AtomicLong DROPPED = new AtomicLong();

    /**
     * Whether anything is emptying the queue yet.
     *
     * <p>Until something is, a full queue keeps what it holds and turns new lines away, because what
     * it holds is the start of the instance and there is no second chance at that. Once lines are
     * being written, the newest are worth more than the oldest and the policy turns around.
     */
    private static final AtomicBoolean DRAINING = new AtomicBoolean();

    /**
     * Says whether something is emptying the queue now.
     */
    public static void draining(boolean draining) {
        DRAINING.set(draining);
    }

    /**
     * One line on its way to the database.
     *
     * @param loggedAt  when it was logged
     * @param level     its severity
     * @param logger    which logger emitted it
     * @param thread    the thread it came from
     * @param message   the formatted line
     * @param throwable the stack trace it carried, or null
     */
    public record LogLine(
            Instant loggedAt, String level, String logger, String thread, String message, String throwable) {}

    @Override
    protected void append(ILoggingEvent event) {
        if (isExcluded(event.getLoggerName())) return;
        LogLine line = new LogLine(
                Instant.ofEpochMilli(event.getTimeStamp()),
                event.getLevel().toString(),
                event.getLoggerName(),
                event.getThreadName(),
                event.getFormattedMessage(),
                stackTrace(event.getThrowableProxy()));
        if (QUEUE.offer(line)) return;
        if (!DRAINING.get()) {
            DROPPED.incrementAndGet();
            return;
        }
        while (!QUEUE.offer(line)) {
            if (QUEUE.poll() != null) DROPPED.incrementAndGet();
        }
    }

    private static boolean isExcluded(String logger) {
        if (logger == null) return true;
        for (String excluded : EXCLUDED) {
            if (logger.startsWith(excluded)) return true;
        }
        return false;
    }

    /**
     * Takes up to {@code max} lines off the queue.
     */
    public static List<LogLine> drain(int max) {
        List<LogLine> batch = new ArrayList<>(Math.min(max, CAPACITY));
        QUEUE.drainTo(batch, max);
        return batch;
    }

    /**
     * How many lines have been dropped because the queue was full, since the process started.
     */
    public static long dropped() {
        return DROPPED.get();
    }

    /**
     * Throws away everything waiting, which is what switching the database log off means for lines
     * already queued.
     */
    public static void discard() {
        QUEUE.clear();
    }

    private static String stackTrace(IThrowableProxy throwable) {
        if (throwable == null) return null;
        StringBuilder out = new StringBuilder();
        IThrowableProxy current = throwable;
        while (current != null) {
            out.append(current.getClassName())
                    .append(": ")
                    .append(current.getMessage())
                    .append('\n');
            for (StackTraceElementProxy element : current.getStackTraceElementProxyArray()) {
                out.append("\tat ").append(element.getSTEAsString()).append('\n');
            }
            current = current.getCause();
            if (current != null) out.append("Caused by: ");
        }
        return out.toString();
    }
}
