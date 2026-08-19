/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import ch.qos.logback.classic.Level;
import dev.chojo.ember.conf.file.elements.Logging;
import dev.chojo.ember.feature.system.repository.ApplicationLogRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Moves log lines from the appender's queue into the database, and keeps the table within its
 * retention.
 *
 * <p>Separate from the appender because the appender exists before the database does. This starts
 * once everything is wired, drains what accumulated in the meantime, and keeps draining.
 *
 * <p>Its own failures are deliberately not logged through logback at anything the appender would
 * capture: a database that cannot be written to would otherwise produce a line about not being able
 * to write, which would be queued, which would fail. They go out at TRACE on this logger, which the
 * appender excludes.
 */
@Singleton
public class ApplicationLogWriter {

    private static final Logger log = LoggerFactory.getLogger(ApplicationLogWriter.class);

    /** How often the queue is emptied. Often enough that the viewer feels current. */
    private static final long FLUSH_SECONDS = 2;

    /** How often old lines are removed. Hourly rather than daily, so a burst cannot sit all day. */
    private static final long PRUNE_MINUTES = 60;

    /** How many lines go in one drain. Bounds the work of a single pass. */
    private static final int BATCH = 500;

    private final Logging config;
    private final ApplicationLogRepository repository;
    private ScheduledExecutorService scheduler;
    private volatile boolean tableReady;

    @Inject
    public ApplicationLogWriter(Logging config, ApplicationLogRepository repository) {
        this.config = config;
        this.repository = repository;
    }

    /**
     * Starts draining and pruning.
     */
    public void start() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "application-log-writer");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::flush, FLUSH_SECONDS, FLUSH_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::pruneNow, 1, PRUNE_MINUTES, TimeUnit.MINUTES);
        log.info("Application log writer started; database log is {}", config.databaseEnabled() ? "on" : "off");
    }

    /**
     * Stops draining. Anything still queued is left where it is.
     */
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        scheduler = null;
        DatabaseLogAppender.draining(false);
    }

    private void flush() {
        try {
            if (!config.databaseEnabled()) {
                DatabaseLogAppender.discard();
                return;
            }
            if (!tableReady && !(tableReady = repository.exists())) return;
            DatabaseLogAppender.draining(true);
            Level threshold = Level.toLevel(config.databaseLevel(), Level.DEBUG);
            var batch = DatabaseLogAppender.drain(BATCH).stream()
                    .filter(line -> Level.toLevel(line.level(), Level.TRACE).isGreaterOrEqual(threshold))
                    .toList();
            repository.write(batch);
        } catch (Exception e) {
            log.trace("Could not write the application log to the database", e);
        }
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
}
