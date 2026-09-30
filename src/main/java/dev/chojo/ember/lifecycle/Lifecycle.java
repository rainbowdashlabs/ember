/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

/**
 * Owns the one JVM shutdown hook and the order in which the instance comes down.
 *
 * <p>On a stop the HTTP server stops accepting and lets running requests finish, the scheduler stops
 * starting work and gives what runs the rest of the budget, every {@link ShutdownFlush} writes what it
 * still buffers, the storage backends close their connections, and the connection pool closes last, so
 * every flush still has a database to write to.
 * The whole sequence shares one budget of {@link #DRAIN_BUDGET}, which the shipped compose files match
 * with a stop grace period of thirty seconds. Every stage is logged with how long it took, and a stage
 * that fails is logged and does not keep the ones after it from running.
 */
@Singleton
public final class Lifecycle {
    /** The time the whole shutdown may take, a few seconds below the container's stop grace period. */
    public static final Duration DRAIN_BUDGET = Duration.ofSeconds(25);

    /** The part of the budget kept back from the scheduled tasks for the flushes and closing the pool. */
    static final Duration FLUSH_RESERVE = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(Lifecycle.class);

    private final ApiServer apiServer;
    private final TaskScheduler scheduler;
    private final Set<ShutdownFlush> flushes;
    private final StorageBackendResolver storage;
    private final DataSource dataSource;
    private final Clock clock;

    /**
     * Creates the lifecycle over everything that has to come down in order.
     *
     * @param apiServer  the HTTP server
     * @param scheduler  the background work
     * @param flushes    the buffers to write before the pool closes
     * @param storage    the storage backends, some of which hold connections of their own
     * @param dataSource the connection pool
     */
    @Inject
    public Lifecycle(
            ApiServer apiServer,
            TaskScheduler scheduler,
            Set<ShutdownFlush> flushes,
            StorageBackendResolver storage,
            DataSource dataSource) {
        this(apiServer, scheduler, flushes, storage, dataSource, Clock.systemUTC());
    }

    Lifecycle(
            ApiServer apiServer,
            TaskScheduler scheduler,
            Set<ShutdownFlush> flushes,
            StorageBackendResolver storage,
            DataSource dataSource,
            Clock clock) {
        this.apiServer = apiServer;
        this.scheduler = scheduler;
        this.flushes = flushes;
        this.storage = storage;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    /**
     * Registers the shutdown hook. The only call to {@link Runtime#addShutdownHook(Thread)} in the code base.
     */
    public void installShutdownHook() {
        Runtime.getRuntime()
                .addShutdownHook(Thread.ofPlatform().name("shutdown").unstarted(this::shutdown));
    }

    /**
     * Brings the instance down in order within the budget.
     */
    void shutdown() {
        Instant started = clock.instant();
        Instant deadline = started.plus(DRAIN_BUDGET);
        log.info("Shutting down, allowing {} s", DRAIN_BUDGET.toSeconds());
        stage("HTTP server", apiServer::stop);
        stage("scheduled tasks", () -> scheduler.stop(taskGrace(deadline)));
        for (ShutdownFlush flush : orderedFlushes()) {
            stage("flush " + flush.name(), flush::flushAll);
        }
        stage("storage backends", storage::closeAll);
        stage("database pool", this::closePool);
        log.info(
                "Shut down in {} ms", Duration.between(started, clock.instant()).toMillis());
    }

    private Duration taskGrace(Instant deadline) {
        Duration left = Duration.between(clock.instant(), deadline).minus(FLUSH_RESERVE);
        return left.isNegative() ? Duration.ZERO : left;
    }

    private List<ShutdownFlush> orderedFlushes() {
        return flushes.stream()
                .sorted(Comparator.comparingInt(ShutdownFlush::order).thenComparing(ShutdownFlush::name))
                .toList();
    }

    private void closePool() throws Exception {
        if (dataSource instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    private void stage(String name, Stage stage) {
        Instant begin = clock.instant();
        try {
            stage.run();
            log.info(
                    "Shutdown: {} done in {} ms",
                    name,
                    Duration.between(begin, clock.instant()).toMillis());
        } catch (Exception e) {
            log.error("Shutdown: {} failed", name, e);
        }
    }

    /** One step of the shutdown, allowed to throw. */
    @FunctionalInterface
    private interface Stage {
        void run() throws Exception;
    }
}
