/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.system.repository.ProblemLogRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.ShutdownFlush;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Writes what the problem log gathered to the database, every few seconds and once more on shutdown.
 *
 * <p>Each problem is one statement. A refused problem is put back and the next one is tried: when that one
 * is refused as well, the database is away, and every problem not yet written goes back to the appender
 * for the next run, so an outage only delays what was gathered. A refusal counts against a problem only
 * when the database is shown to work in the same run, by the next write going through or, for the last
 * one, by the database answering at all; such a problem is refused for what it holds and is dropped after {@link #MAX_FAILED_WRITES} runs instead of
 * being kept forever. The first failed run after a good one is logged as a warning, which the problem log leaves
 * out because it is logged while writing.
 */
@Singleton
public class ProblemLogFlusher implements TaskSource, ShutdownFlush {
    private static final Logger log = LoggerFactory.getLogger(ProblemLogFlusher.class);

    private static final Duration INTERVAL = Duration.ofSeconds(5);

    /** How many runs in a row one problem may be refused before it is dropped. */
    static final int MAX_FAILED_WRITES = 5;

    private final ProblemLogRepository repository;
    private final Supplier<@Nullable ProblemLogAppender> appender;
    private volatile boolean failing;

    @Inject
    public ProblemLogFlusher(ProblemLogRepository repository) {
        this(repository, ProblemLogAppender::instance);
    }

    ProblemLogFlusher(ProblemLogRepository repository, Supplier<@Nullable ProblemLogAppender> appender) {
        this.repository = repository;
        this.appender = appender;
    }

    /**
     * Writes everything gathered so far.
     *
     * @return how many problems were written
     */
    int flush() {
        return flush(false);
    }

    /**
     * Writes everything gathered so far.
     *
     * @param shuttingDown whether the application is stopping, which has no time to tell a refused problem
     *                     from an outage: the first failure then ends the run
     * @return how many problems were written
     */
    private int flush(boolean shuttingDown) {
        var source = appender.get();
        if (source == null) return 0;
        List<PendingProblem> drained = source.drainPending();
        return ProblemLogAppender.writing(() -> write(source, drained, shuttingDown));
    }

    private int write(ProblemLogAppender source, List<PendingProblem> drained, boolean shuttingDown) {
        int written = 0;
        PendingProblem suspect = null;
        for (int i = 0; i < drained.size(); i++) {
            var problem = drained.get(i);
            try {
                repository.record(problem.record());
                written++;
                if (suspect != null) keepOrDrop(source, suspect);
                suspect = null;
            } catch (RuntimeException e) {
                noteFailure(e);
                if (shuttingDown) {
                    if (suspect != null) source.restore(suspect);
                    drained.subList(i, drained.size()).forEach(source::restore);
                    return written;
                }
                if (suspect != null) {
                    source.restore(suspect);
                    drained.subList(i, drained.size()).forEach(source::restore);
                    return written;
                }
                suspect = problem;
            }
        }
        boolean databaseWorks = written > 0 || (suspect != null && repository.reachable());
        if (suspect != null) {
            if (databaseWorks) {
                keepOrDrop(source, suspect);
            } else {
                source.restore(suspect);
            }
        }
        if (databaseWorks) failing = false;
        return written;
    }

    private void keepOrDrop(ProblemLogAppender source, PendingProblem problem) {
        problem.failedOnce();
        if (problem.failedWrites() >= MAX_FAILED_WRITES) {
            log.warn(
                    "Dropped problem {} from the problem log: the database refused it {} times",
                    problem.fingerprint(),
                    MAX_FAILED_WRITES);
            return;
        }
        source.restore(problem);
    }

    private void noteFailure(RuntimeException e) {
        if (failing) {
            log.trace("Could not write the problem log", e);
            return;
        }
        failing = true;
        log.warn("Could not write the problem log; keeping what was gathered for the next run", e);
    }

    @Override
    public String name() {
        return "problem log";
    }

    @Override
    public void flushAll() {
        flush(true);
    }

    /** Runs late, just before the application log, so what the earlier flushes log is still written. */
    @Override
    public int order() {
        return Integer.MAX_VALUE - 1;
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask("problem-log-flush", Schedule.fixedDelay(INTERVAL, INTERVAL), this::flush));
    }
}
