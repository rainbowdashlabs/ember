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

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one place background work runs: periodic {@link ScheduledTask}s, one-shot delayed work, fire and
 * forget jobs and ordered {@link SerialLane}s.
 *
 * <p>A single platform thread only keeps time and hands every run to a virtual thread of its own, so a
 * slow or blocking run holds nothing but itself. A task never overlaps itself: a fixed-delay task plans its
 * next run when the current one returns, and a fixed-rate run that falls due while the previous one is
 * still busy is skipped. Every run is guarded, so an exception is logged with the task's name and the
 * schedule carries on.
 *
 * <p>Nothing runs before {@link #start(Collection)}, which the bootstrapper calls once the schema is
 * certain and the demo data is in place, and nothing new starts once {@link #stop(Duration)} has been
 * called by the shutdown hook.
 */
@Singleton
public final class TaskScheduler {
    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);
    private static final Duration INTERRUPT_WAIT = Duration.ofSeconds(1);

    private final ScheduledThreadPoolExecutor timer;
    private final ExecutorService workers;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean stopping;

    /**
     * Creates the scheduler. No thread starts until the first piece of work is handed to it.
     */
    @Inject
    public TaskScheduler() {
        timer = new ScheduledThreadPoolExecutor(
                1, Thread.ofPlatform().daemon().name("task-timer").factory());
        timer.setRemoveOnCancelPolicy(true);
        timer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        timer.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        workers = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("background-", 0).factory());
    }

    /**
     * Plans every task according to its schedule. Called once.
     *
     * @param tasks the tasks registered through the multibinder
     * @throws IllegalStateException when the scheduler has been started before
     */
    public void start(Collection<? extends ScheduledTask> tasks) {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("The task scheduler has already been started");
        }
        tasks.stream()
                .sorted(Comparator.comparing(ScheduledTask::name))
                .map(TaskRunner::new)
                .forEach(TaskRunner::arm);
        log.info("Started {} scheduled tasks", tasks.size());
    }

    /**
     * Runs a piece of work once after a delay.
     *
     * @param name  the name the work runs and is logged under
     * @param delay the wait before it runs
     * @param work  the work
     */
    public void later(String name, Duration delay, Runnable work) {
        after(delay, () -> submit(name, work));
    }

    /**
     * Runs a piece of work now, on a virtual thread of its own.
     *
     * @param name the name the work runs and is logged under
     * @param work the work
     */
    public void background(String name, Runnable work) {
        submit(name, work);
    }

    /**
     * An ordered queue whose items run one at a time, in the order they were handed in.
     *
     * @param name the name the lane's items run and are logged under
     * @return a new lane
     */
    public SerialLane lane(String name) {
        return new SerialLane(name, this);
    }

    /**
     * The virtual thread executor for work that manages its own results, such as a federation fan-out
     * waiting on its futures. Unlike {@link #background(String, Runnable)} nothing is caught for the
     * caller, and once the scheduler has stopped the executor refuses new work.
     *
     * @return the shared executor
     */
    public Executor executor() {
        return workers;
    }

    /**
     * Whether the scheduler has been told to stop.
     *
     * @return {@code true} once {@link #stop(Duration)} has been called
     */
    public boolean isStopping() {
        return stopping;
    }

    /**
     * Stops the scheduler: nothing new runs, running work gets the grace period to finish, and whatever is
     * still running after it is interrupted.
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

    /**
     * Hands a piece of work to a virtual thread, guarded. Answers whether it was accepted, which it is
     * not once the scheduler is stopping.
     */
    boolean submit(String name, Runnable work) {
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
     * Runs a piece of work under its own name and logs whatever it throws, so no failure ends a schedule
     * or a lane. An interruption while the scheduler stops is expected and only logged at debug.
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
     * Drives one task: arms its schedule and makes sure no two of its runs overlap.
     */
    private final class TaskRunner {
        private final ScheduledTask task;
        private final Schedule schedule;
        private final AtomicBoolean busy = new AtomicBoolean();

        private TaskRunner(ScheduledTask task) {
            this.task = task;
            this.schedule = task.schedule();
        }

        private void arm() {
            if (schedule.mode() == Schedule.Mode.FIXED_RATE) {
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
            if (!submit(task.name(), this::runOnce)) {
                busy.set(false);
            }
        }

        private void runOnce() {
            try {
                task.run();
            } finally {
                busy.set(false);
                if (schedule.mode() == Schedule.Mode.FIXED_DELAY && !stopping) {
                    after(schedule.period(), this::fire);
                }
            }
        }
    }
}
