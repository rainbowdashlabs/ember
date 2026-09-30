/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers, per scheduled task, when it last ran, how long that took and how it ended.
 *
 * <p>Memory only, by design: the page that reads it answers whether the sweeps are running right now,
 * and a restart is the one moment that question starts over. A run that catches and logs its own
 * failure counts as succeeded here; only what reaches the scheduler shows as a failure.
 */
final class TaskStatusBoard {
    static final int MAX_MESSAGE_LENGTH = 500;

    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    TaskStatusBoard(Clock clock) {
        this.clock = clock;
    }

    void register(ScheduledTask task) {
        entries.put(task.name(), new Entry(task.name(), task.schedule()));
    }

    Instant started(String name) {
        Instant now = clock.instant();
        entry(name).started(now);
        return now;
    }

    void succeeded(String name, Instant startedAt) {
        entry(name).finished(Duration.between(startedAt, clock.instant()), null);
    }

    void failed(String name, Instant startedAt, Throwable failure) {
        entry(name).finished(Duration.between(startedAt, clock.instant()), describe(failure));
    }

    List<TaskStatus> snapshot() {
        return entries.values().stream()
                .map(Entry::status)
                .sorted(Comparator.comparing(TaskStatus::name))
                .toList();
    }

    private Entry entry(String name) {
        var entry = entries.get(name);
        if (entry == null) throw new IllegalArgumentException("Unknown task " + name);
        return entry;
    }

    private static String describe(Throwable failure) {
        String text = failure.getMessage() == null
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + failure.getMessage();
        return text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) : text;
    }

    /** The mutable record of one task, guarded by its own monitor. */
    private static final class Entry {
        private final String name;
        private final Schedule schedule;
        private boolean running;
        private TaskOutcome lastOutcome = TaskOutcome.NOT_RUN_YET;
        private Instant lastStartedAt;
        private Long lastDurationMs;
        private long runs;
        private long failures;
        private Instant lastFailureAt;
        private String lastFailureMessage;

        private Entry(String name, Schedule schedule) {
            this.name = name;
            this.schedule = schedule;
        }

        private synchronized void started(Instant at) {
            running = true;
            lastStartedAt = at;
        }

        private synchronized void finished(Duration took, String failureMessage) {
            running = false;
            runs++;
            lastDurationMs = took.toMillis();
            if (failureMessage == null) {
                lastOutcome = TaskOutcome.SUCCEEDED;
                return;
            }
            lastOutcome = TaskOutcome.FAILED;
            failures++;
            lastFailureAt = lastStartedAt;
            lastFailureMessage = failureMessage;
        }

        private synchronized TaskStatus status() {
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
    }
}
