/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

import dev.chojo.ember.MovableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaskHistoryTest {
    private static final Instant START = Instant.parse("2026-05-01T08:00:00Z");

    private final MovableClock clock = new MovableClock(START);
    private final TaskHistory history = new TaskHistory(
            new ScheduledTask("sweep", Schedule.fixedDelay(Duration.ofMinutes(1), Duration.ofMinutes(15)), () -> {}),
            clock);

    @Test
    void aNewTaskHasNotRunYet() {
        var status = history.status();

        assertEquals("sweep", status.name());
        assertEquals(Schedule.Mode.FIXED_DELAY, status.mode());
        assertEquals(900, status.periodSeconds());
        assertEquals(TaskOutcome.NOT_RUN_YET, status.outcome());
        assertNull(status.lastStartedAt());
        assertNull(status.lastDurationMs());
        assertEquals(0, status.runs());
    }

    @Test
    void aRunningTaskShowsAsRunningAndThenSucceeded() {
        history.started();
        assertEquals(TaskOutcome.RUNNING, history.status().outcome());
        clock.advance(Duration.ofMillis(1500));
        history.succeeded();

        var status = history.status();
        assertEquals(TaskOutcome.SUCCEEDED, status.outcome());
        assertEquals(START, status.lastStartedAt());
        assertEquals(1500, status.lastDurationMs());
        assertEquals(1, status.runs());
        assertEquals(0, status.failures());
    }

    @Test
    void aFailureIsKeptAfterTheNextRunSucceeds() {
        history.started();
        history.failed(new IllegalStateException("database gone"));
        clock.advance(Duration.ofMinutes(15));
        history.started();
        history.succeeded();

        var status = history.status();
        assertEquals(TaskOutcome.SUCCEEDED, status.outcome());
        assertEquals(2, status.runs());
        assertEquals(1, status.failures());
        assertEquals(START, status.lastFailureAt());
        assertEquals("IllegalStateException: database gone", status.lastFailureMessage());
    }

    @Test
    void aLongFailureMessageIsCutAndAMissingOneNamesTheException() {
        history.started();
        history.failed(new IllegalStateException("x".repeat(1_000)));
        assertEquals(
                TaskHistory.MAX_MESSAGE_LENGTH,
                history.status().lastFailureMessage().length());

        history.started();
        history.failed(new NullPointerException());
        assertEquals("NullPointerException", history.status().lastFailureMessage());
    }
}
