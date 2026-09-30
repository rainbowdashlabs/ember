/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The appointment sweeps keep the names and the cadence they had when each started a thread of its own.
 */
class EventTasksTest {

    @Test
    void reminderCheckRunsEveryThirtyMinutes() {
        assertTask(
                new EventReminderChecker.Task(mock(EventReminderChecker.class)),
                "event-reminder-check",
                Duration.ofMinutes(5),
                Duration.ofMinutes(30));
    }

    @Test
    void thresholdCheckRunsEveryThirtyMinutes() {
        assertTask(
                new EventThresholdChecker.Task(mock(EventThresholdChecker.class)),
                "event-threshold-check",
                Duration.ofMinutes(5),
                Duration.ofMinutes(30));
    }

    @Test
    void aFailedThresholdCheckIsSwallowed() {
        var events = mock(EventRepository.class);
        when(events.findAutoCancel()).thenThrow(new IllegalStateException("database gone"));
        var task = new EventThresholdChecker.Task(
                new EventThresholdChecker(events, mock(EventCrudService.class), mock(StationReadOnlyGuard.class)));

        assertDoesNotThrow(task::run);
    }

    @Test
    void deadlineCheckRunsEveryFiveMinutes() {
        assertTask(
                new RegistrationDeadlineChecker.Task(mock(RegistrationDeadlineChecker.class)),
                "registration-deadline-check",
                Duration.ofMinutes(5),
                Duration.ofMinutes(5));
    }

    @Test
    void fieldRegistrationSweepRunsTwiceADay() {
        assertTask(
                new FieldRegistrationSweeper.Task(mock(FieldRegistrationSweeper.class)),
                "field-registration-sweep",
                Duration.ofMinutes(2),
                Duration.ofHours(12));
    }

    @Test
    void settledRefusalSweepRunsTheSweep() {
        var sweeper = mock(SettledRefusalSweeper.class);
        var task = new SettledRefusalSweeper.Task(sweeper);

        task.run();

        verify(sweeper).sweep();
        assertTask(task, "settled-refusal-sweep", Duration.ofMinutes(1), Duration.ofMinutes(5));
    }

    private static void assertTask(ScheduledTask task, String name, Duration initialDelay, Duration period) {
        assertEquals(name, task.name());
        assertEquals(Schedule.fixedDelay(initialDelay, period), task.schedule());
    }
}
