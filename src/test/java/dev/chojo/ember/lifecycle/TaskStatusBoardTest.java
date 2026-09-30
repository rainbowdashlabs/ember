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
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskStatusBoardTest {
    private static final Instant START = Instant.parse("2026-05-01T08:00:00Z");

    private final MovableClock clock = new MovableClock(START);
    private final TaskStatusBoard board = new TaskStatusBoard(clock);

    private static ScheduledTask task(String name) {
        return new DelegatingTask(
                name, Schedule.fixedDelay(Duration.ofMinutes(1), Duration.ofMinutes(15)), () -> {}) {};
    }

    @Test
    void aRegisteredTaskHasNotRunYet() {
        board.register(task("sweep"));

        var status = board.snapshot().getFirst();

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
        board.register(task("sweep"));

        Instant started = board.started("sweep");
        assertEquals(TaskOutcome.RUNNING, board.snapshot().getFirst().outcome());
        clock.advance(Duration.ofMillis(1500));
        board.succeeded("sweep", started);

        var status = board.snapshot().getFirst();
        assertEquals(TaskOutcome.SUCCEEDED, status.outcome());
        assertEquals(START, status.lastStartedAt());
        assertEquals(1500, status.lastDurationMs());
        assertEquals(1, status.runs());
        assertEquals(0, status.failures());
    }

    @Test
    void aFailureIsKeptAfterTheNextRunSucceeds() {
        board.register(task("sweep"));

        Instant first = board.started("sweep");
        board.failed("sweep", first, new IllegalStateException("database gone"));
        clock.advance(Duration.ofMinutes(15));
        Instant second = board.started("sweep");
        board.succeeded("sweep", second);

        var status = board.snapshot().getFirst();
        assertEquals(TaskOutcome.SUCCEEDED, status.outcome());
        assertEquals(2, status.runs());
        assertEquals(1, status.failures());
        assertEquals(START, status.lastFailureAt());
        assertEquals("IllegalStateException: database gone", status.lastFailureMessage());
    }

    @Test
    void aLongFailureMessageIsCutAndAMissingOneNamesTheException() {
        board.register(task("sweep"));

        board.failed("sweep", board.started("sweep"), new IllegalStateException("x".repeat(1_000)));
        assertEquals(
                TaskStatusBoard.MAX_MESSAGE_LENGTH,
                board.snapshot().getFirst().lastFailureMessage().length());

        board.failed("sweep", board.started("sweep"), new NullPointerException());
        assertEquals("NullPointerException", board.snapshot().getFirst().lastFailureMessage());
    }

    @Test
    void theSnapshotIsSortedByName() {
        board.register(task("b-task"));
        board.register(task("a-task"));

        assertEquals("a-task", board.snapshot().getFirst().name());
    }

    @Test
    void anUnknownTaskIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> board.started("missing"));
    }
}
