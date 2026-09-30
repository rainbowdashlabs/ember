/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * What one scheduled task has done since the instance started, for the task status page.
 *
 * <p>Memory only on purpose: the page answers whether the sweeps run right now, and a restart is where that
 * question starts over. A run that catches its own failure counts as succeeded; only what reaches the scheduler
 * is a failure.
 */
final class TaskHistory {
    static final int MAX_MESSAGE_LENGTH = 500;

    private final String name;
    private final Schedule schedule;
    private final Clock clock;
    private boolean running;
    private TaskOutcome lastOutcome = TaskOutcome.NOT_RUN_YET;
    private Instant lastStartedAt;
    private Long lastDurationMs;
    private long runs;
    private long failures;
    private Instant lastFailureAt;
    private String lastFailureMessage;

    TaskHistory(ScheduledTask task, Clock clock) {
        this.name = task.name();
        this.schedule = task.schedule();
        this.clock = clock;
    }

    synchronized void started() {
        running = true;
        lastStartedAt = clock.instant();
    }

    synchronized void succeeded() {
        finish();
        lastOutcome = TaskOutcome.SUCCEEDED;
    }

    synchronized void failed(Throwable failure) {
        finish();
        lastOutcome = TaskOutcome.FAILED;
        failures++;
        lastFailureAt = lastStartedAt;
        lastFailureMessage = describe(failure);
    }

    synchronized TaskStatus status() {
        return new TaskStatus(
                name,
                schedule.mode(),
                schedule.period().toSeconds(),
                running ? TaskOutcome.RUNNING : lastOutcome,
                lastStartedAt,
                lastDurationMs,
                runs,
                failures,
                lastFailureAt,
                lastFailureMessage);
    }

    private void finish() {
        running = false;
        runs++;
        lastDurationMs = Duration.between(lastStartedAt, clock.instant()).toMillis();
    }

    private static String describe(Throwable failure) {
        String type = failure.getClass().getSimpleName();
        String text = failure.getMessage() == null ? type : type + ": " + failure.getMessage();
        return text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) : text;
    }
}
