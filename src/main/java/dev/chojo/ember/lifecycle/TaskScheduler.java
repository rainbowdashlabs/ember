/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one place background work runs: {@link ScheduledTask}s, delayed and fire and forget work, and
 * {@link SerialLane}s.
 *
 * <p>One platform thread keeps time and hands every run to a virtual thread of its own, so a blocking run holds
 * nothing but itself. Every run is guarded: an exception is logged under the work's name and the schedule
 * carries on. Nothing new starts once {@link #stop(Duration)} has been called.
 */
@Singleton
public final class TaskScheduler {
    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);
    private static final Duration INTERRUPT_WAIT = Duration.ofSeconds(1);

    private final Clock clock;
    private final ScheduledThreadPoolExecutor timer;
    private final ExecutorService workers;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile List<TaskRunner> runners = List.of();
    private volatile boolean stopping;

    /**
     * Creates the scheduler. No thread starts until the first piece of work is handed to it.
     */
    @Inject
    public TaskScheduler() {
        this(Clock.systemUTC());
    }

    TaskScheduler(Clock clock) {
        this.clock = clock;
        timer = new ScheduledThreadPoolExecutor(
                1, Thread.ofPlatform().daemon().name("task-timer").factory());
        timer.setRemoveOnCancelPolicy(true);
        timer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        timer.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        workers = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("background-", 0).factory());
    }

    /**
     * Plans every task according to its schedule. The bootstrapper calls it once the schema is certain.
     *
     * @param tasks the tasks of every {@link TaskSource}
     * @throws IllegalStateException when the scheduler has been started before
     */
    public void start(Collection<ScheduledTask> tasks) {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("The task scheduler has already been started");
        }
        runners = tasks.stream()
                .sorted(Comparator.comparing(ScheduledTask::name))
                .map(TaskRunner::new)
                .toList();
        runners.forEach(TaskRunner::arm);
        log.info("Started {} scheduled tasks", tasks.size());
    }

    /**
     * What every scheduled task has done since the start.
     *
     * @return one status per task, by name
     */
    public List<TaskStatus> statuses() {
        return runners.stream().map(runner -> runner.history.status()).toList();
    }

    /**
     * Runs a piece of work once after a delay.
     *
     * @param name  the name the work runs and is logged under
     * @param delay the wait before it runs
     * @param work  the work
     */
    public void later(String name, Duration delay, Runnable work) {
        after(delay, () -> background(name, work));
    }

    /**
     * Runs a piece of work now, guarded, on a virtual thread of its own.
     *
     * @param name the name the work runs and is logged under
     * @param work the work
     * @return {@code false} when the scheduler is stopping and the work was not accepted
     */
    public boolean background(String name, Runnable work) {
        if (stopping) {
            log.debug("Not running {}: the scheduler is stopping", name);
            return false;
        }
        try {
            workers.execute(() -> runGuarded(name, work));
            return true;
        } catch (RejectedExecutionException e) {
            log.debug("Not running {}: the scheduler has stopped", name);
            return false;
        }
    }

    /**
     * An unbounded {@link SerialLane}.
     *
     * @param name the name the lane's items run and are logged under
     * @return a new lane
     */
    public SerialLane lane(String name) {
        return lane(name, Integer.MAX_VALUE);
    }

    /**
     * A {@link SerialLane} that refuses items once the given number are waiting.
     *
     * @param name     the name the lane's items run and are logged under
     * @param capacity how many items may wait at once
     * @return a new lane
     */
    public SerialLane lane(String name, int capacity) {
        return new SerialLane(name, capacity, this);
    }

    /**
     * The virtual thread executor for work that handles its own results, such as a fan-out waiting on its
     * futures. Nothing is caught for the caller, and after {@link #stop(Duration)} it refuses new work.
     *
     * @return the shared executor
     */
    public Executor executor() {
        return workers;
    }

    /**
     * Stops the scheduler: nothing new runs, running work gets the grace period to finish and is interrupted
     * after it.
     *
     * @param grace how long running work may take to finish
     */
    public void stop(Duration grace) {
        stopping = true;
        timer.shutdownNow();
        workers.shutdown();
        if (awaitWorkers(grace)) return;
        log.warn("Background work was still running after {} ms and is interrupted", grace.toMillis());
        workers.shutdownNow();
        if (!awaitWorkers(INTERRUPT_WAIT)) {
            log.warn("Background work did not end after being interrupted");
        }
    }

    boolean isStopping() {
        return stopping;
    }

    /**
     * Runs a piece of work under its own thread name and logs whatever it throws.
     */
    static void runGuarded(String name, Runnable work) {
        var thread = Thread.currentThread();
        String previousName = thread.getName();
        thread.setName(name);
        try {
            work.run();
        } catch (RuntimeException e) {
            log.error("Background work {} failed", name, e);
        } finally {
            thread.setName(previousName);
        }
    }

    private void after(Duration delay, Runnable action) {
        try {
            timer.schedule(action, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.debug("Not planning more work: the scheduler has stopped");
        }
    }

    private boolean awaitWorkers(Duration wait) {
        try {
            return workers.awaitTermination(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Drives one task. A fixed-delay task plans its next run when the current one returns; a fixed-rate run
     * that falls due while the previous one is busy is skipped, so no two runs overlap.
     */
    private final class TaskRunner {
        private final ScheduledTask task;
        private final TaskHistory history;
        private final AtomicBoolean busy = new AtomicBoolean();

        private TaskRunner(ScheduledTask task) {
            this.task = task;
            this.history = new TaskHistory(task, clock);
        }

        private void arm() {
            var schedule = task.schedule();
            if (schedule.mode() == Schedule.ScheduleMode.FIXED_RATE) {
                timer.scheduleAtFixedRate(
                        this::fire,
                        schedule.initialDelay().toMillis(),
                        schedule.period().toMillis(),
                        TimeUnit.MILLISECONDS);
            } else {
                after(schedule.initialDelay(), this::fire);
            }
        }

        private void fire() {
            if (!busy.compareAndSet(false, true)) {
                log.debug("Skipping a run of {}: the previous one is still busy", task.name());
                return;
            }
            if (!background(task.name(), this::runOnce)) {
                busy.set(false);
            }
        }

        private void runOnce() {
            history.started();
            try {
                task.work().run();
                history.succeeded();
            } catch (RuntimeException e) {
                history.failed(e);
                throw e;
            } finally {
                busy.set(false);
                if (task.schedule().mode() == Schedule.ScheduleMode.FIXED_DELAY && !stopping) {
                    after(task.schedule().period(), this::fire);
                }
            }
        }
    }
}
