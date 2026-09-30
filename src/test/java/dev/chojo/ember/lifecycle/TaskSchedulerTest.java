/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskSchedulerTest {
    private static final Duration TICK = Duration.ofMillis(20);

    private final TaskScheduler scheduler = new TaskScheduler();

    @AfterEach
    void stop() {
        scheduler.stop(Duration.ofSeconds(1));
    }

    @Test
    void fixedDelayTaskRunsRepeatedly() throws InterruptedException {
        var runs = new CountDownLatch(3);
        scheduler.start(List.of(task("repeat", Schedule.fixedDelay(Duration.ZERO, TICK), runs::countDown)));

        assertTrue(runs.await(2, TimeUnit.SECONDS), "a fixed-delay task must keep running");
    }

    @Test
    void fixedRateTaskRunsRepeatedly() throws InterruptedException {
        var runs = new CountDownLatch(3);
        scheduler.start(List.of(task("rate", Schedule.fixedRate(Duration.ZERO, TICK), runs::countDown)));

        assertTrue(runs.await(2, TimeUnit.SECONDS), "a fixed-rate task must keep running");
    }

    @Test
    void fixedRateRunIsSkippedWhileThePreviousOneIsBusy() throws InterruptedException {
        var release = new CountDownLatch(1);
        var concurrent = new AtomicInteger();
        var overlapped = new AtomicBoolean();
        var started = new CountDownLatch(1);
        scheduler.start(List.of(task("slow", Schedule.fixedRate(Duration.ZERO, Duration.ofMillis(5)), () -> {
            if (concurrent.incrementAndGet() > 1) overlapped.set(true);
            started.countDown();
            try {
                release.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                concurrent.decrementAndGet();
            }
        })));

        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);
        release.countDown();

        assertFalse(overlapped.get(), "a run must not start while the previous one is still busy");
    }

    @Test
    void onceTaskRunsExactlyOnce() throws InterruptedException {
        var runs = new AtomicInteger();
        var ran = new CountDownLatch(1);
        scheduler.start(List.of(task("once", Schedule.once(Duration.ZERO), () -> {
            runs.incrementAndGet();
            ran.countDown();
        })));

        assertTrue(ran.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertEquals(1, runs.get());
    }

    @Test
    void failingRunKeepsTheSchedule() throws InterruptedException {
        var runs = new CountDownLatch(3);
        scheduler.start(List.of(task("failing", Schedule.fixedDelay(Duration.ZERO, TICK), () -> {
            runs.countDown();
            throw new IllegalStateException("expected by the test");
        })));

        assertTrue(runs.await(2, TimeUnit.SECONDS), "a run that throws must not end the schedule");
    }

    @Test
    void statusesRecordRunsAndFailures() throws InterruptedException {
        var runs = new CountDownLatch(2);
        scheduler.start(List.of(
                task("fine", Schedule.once(Duration.ZERO), runs::countDown),
                task("broken", Schedule.once(Duration.ZERO), () -> {
                    runs.countDown();
                    throw new IllegalStateException("expected by the test");
                })));
        assertTrue(runs.await(1, TimeUnit.SECONDS));
        Thread.sleep(50);

        var statuses = scheduler.statuses();

        assertEquals(
                List.of("broken", "fine"),
                statuses.stream().map(TaskStatus::name).toList());
        assertEquals(TaskOutcome.FAILED, statuses.get(0).outcome());
        assertEquals(
                "IllegalStateException: expected by the test", statuses.get(0).lastFailureMessage());
        assertEquals(TaskOutcome.SUCCEEDED, statuses.get(1).outcome());
    }

    @Test
    void runsUnderTheTaskName() throws InterruptedException {
        var name = new String[1];
        var ran = new CountDownLatch(1);
        scheduler.start(List.of(task("named-task", Schedule.once(Duration.ZERO), () -> {
            name[0] = Thread.currentThread().getName();
            ran.countDown();
        })));

        assertTrue(ran.await(1, TimeUnit.SECONDS));
        assertEquals("named-task", name[0]);
    }

    @Test
    void startsOnlyOnce() {
        scheduler.start(List.of());

        assertThrows(IllegalStateException.class, () -> scheduler.start(List.of()));
    }

    @Test
    void laterRunsAfterTheDelay() throws InterruptedException {
        var ran = new CountDownLatch(1);
        scheduler.later("later", TICK, ran::countDown);

        assertTrue(ran.await(1, TimeUnit.SECONDS));
    }

    @Test
    void backgroundRunsAndSurvivesAFailure() throws InterruptedException {
        var ran = new CountDownLatch(1);
        scheduler.background("failing", () -> {
            throw new IllegalStateException("expected by the test");
        });
        scheduler.background("background", ran::countDown);

        assertTrue(ran.await(1, TimeUnit.SECONDS));
    }

    @Test
    void executorRunsWork() throws InterruptedException {
        var ran = new CountDownLatch(1);
        scheduler.executor().execute(ran::countDown);

        assertTrue(ran.await(1, TimeUnit.SECONDS));
    }

    @Test
    void nothingRunsAfterStop() throws InterruptedException {
        var runs = new AtomicInteger();
        scheduler.start(
                List.of(task("stopped", Schedule.fixedDelay(Duration.ofMillis(50), TICK), runs::incrementAndGet)));
        scheduler.stop(Duration.ofMillis(100));
        scheduler.background("after-stop", runs::incrementAndGet);
        scheduler.later("later-after-stop", Duration.ZERO, runs::incrementAndGet);

        Thread.sleep(150);
        assertEquals(0, runs.get());
        assertTrue(scheduler.isStopping());
    }

    @Test
    void stopInterruptsWorkThatOutlivesTheGrace() throws InterruptedException {
        var interrupted = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        scheduler.background("stuck", () -> {
            started.countDown();
            try {
                Thread.sleep(Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        });
        assertTrue(started.await(1, TimeUnit.SECONDS));

        scheduler.stop(Duration.ofMillis(50));

        assertTrue(interrupted.await(1, TimeUnit.SECONDS), "work past the grace must be interrupted");
    }

    @Test
    void stopWaitsForRunningWorkWithinTheGrace() throws InterruptedException {
        var finished = new AtomicBoolean();
        var started = new CountDownLatch(1);
        scheduler.background("short", () -> {
            started.countDown();
            try {
                Thread.sleep(50);
                finished.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(1, TimeUnit.SECONDS));

        scheduler.stop(Duration.ofSeconds(1));

        assertTrue(finished.get(), "work inside the grace must be allowed to finish");
    }

    @Test
    void scheduleRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> Schedule.fixedDelay(Duration.ofSeconds(-1), TICK));
        assertThrows(IllegalArgumentException.class, () -> Schedule.fixedRate(Duration.ZERO, Duration.ZERO));
        assertEquals(Duration.ZERO, Schedule.once(TICK).period());
    }

    private static ScheduledTask task(String name, Schedule schedule, Runnable body) {
        return new ScheduledTask() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Schedule schedule() {
                return schedule;
            }

            @Override
            public void run() {
                body.run();
            }
        };
    }
}
