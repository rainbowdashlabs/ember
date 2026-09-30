/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import ch.qos.logback.classic.Level;
import dev.chojo.ember.conf.file.elements.Logging;
import dev.chojo.ember.feature.system.repository.ApplicationLogRepository;
import dev.chojo.ember.lifecycle.DelegatingTask;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ShutdownFlush;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Moves log lines from the appender's queue into the database, and keeps the table within its
 * retention.
 *
 * <p>Separate from the appender because the appender exists before the database does. Its tasks
 * start once the instance has booted and the schema is certain, drain what accumulated in the
 * meantime, and keep draining. On shutdown it flushes last, after every other buffer, so the lines
 * logged while the instance comes down reach the table too.
 *
 * <p>Its own failures are deliberately not logged through logback at anything the appender would
 * capture: a database that cannot be written to would otherwise produce a line about not being able
 * to write, which would be queued, which would fail. They go out at TRACE on this logger, which the
 * appender excludes.
 */
@Singleton
public class ApplicationLogWriter implements ShutdownFlush {

    private static final Logger log = LoggerFactory.getLogger(ApplicationLogWriter.class);

    /** How often the queue is emptied. Often enough that the viewer feels current. */
    private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(2);

    /** How often old lines are removed. Hourly rather than daily, so a burst cannot sit all day. */
    private static final Duration PRUNE_INTERVAL = Duration.ofMinutes(60);

    /** How many lines go in one drain. Bounds the work of a single pass. */
    private static final int BATCH = 500;

    /** How many passes the shutdown flush makes at most: enough for a full appender queue. */
    private static final int MAX_SHUTDOWN_PASSES = 21;

    private final Logging config;
    private final ApplicationLogRepository repository;
    private volatile boolean tableReady;

    @Inject
    public ApplicationLogWriter(Logging config, ApplicationLogRepository repository) {
        this.config = config;
        this.repository = repository;
    }

    /**
     * Writes one batch of queued lines.
     *
     * @return how many lines were taken from the queue, {@code 0} when nothing was written
     */
    int flush() {
        try {
            if (!config.databaseEnabled()) {
                DatabaseLogAppender.discard();
                return 0;
            }
            if (!tableReady && !(tableReady = repository.exists())) return 0;
            DatabaseLogAppender.draining(true);
            Level threshold = Level.toLevel(config.databaseLevel(), Level.DEBUG);
            var drained = DatabaseLogAppender.drain(BATCH);
            var batch = drained.stream()
                    .filter(line -> Level.toLevel(line.level(), Level.TRACE).isGreaterOrEqual(threshold))
                    .toList();
            repository.write(batch);
            return drained.size();
        } catch (Exception e) {
            log.trace("Could not write the application log to the database", e);
            return 0;
        }
    }

    @Override
    public String name() {
        return "application log";
    }

    /**
     * Writes the whole queue, pass by pass, until it is empty or a pass writes nothing.
     */
    @Override
    public void flushAll() {
        for (int pass = 0; pass < MAX_SHUTDOWN_PASSES; pass++) {
            if (flush() == 0) return;
        }
    }

    /**
     * Last among the flushes, so the lines the others log reach the table.
     *
     * @return the highest position
     */
    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    /**
     * Removes what is past retention, now rather than at the next hour.
     */
    public void pruneNow() {
        try {
            if (!config.databaseEnabled()) return;
            if (!tableReady && !(tableReady = repository.exists())) return;
            int removed = repository.prune(config.retentionDays());
            if (removed > 0) log.trace("Removed {} application log line(s) past retention", removed);
        } catch (Exception e) {
            log.trace("Could not prune the application log", e);
        }
    }

    /** Empties the queue into the table every two seconds. */
    @Singleton
    public static final class FlushTask extends DelegatingTask {
        @Inject
        FlushTask(ApplicationLogWriter writer) {
            super("application-log-flush", Schedule.fixedDelay(FLUSH_INTERVAL, FLUSH_INTERVAL), writer::flush);
        }
    }

    /** Removes the lines past retention every hour. */
    @Singleton
    public static final class PruneTask extends DelegatingTask {
        @Inject
        PruneTask(ApplicationLogWriter writer) {
            super(
                    "application-log-prune",
                    Schedule.fixedDelay(Duration.ofMinutes(1), PRUNE_INTERVAL),
                    writer::pruneNow);
        }
    }
}
