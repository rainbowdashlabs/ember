/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
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
                taskOf(mock(EventReminderChecker.class)),
                "event-reminder-check",
                Duration.ofMinutes(5),
                Duration.ofMinutes(30));
    }

    @Test
    void thresholdCheckRunsEveryThirtyMinutes() {
        assertTask(
                taskOf(mock(EventThresholdChecker.class)),
                "event-threshold-check",
                Duration.ofMinutes(5),
                Duration.ofMinutes(30));
    }

    @Test
    void aFailedThresholdCheckIsSwallowed() {
        var events = mock(EventRepository.class);
        when(events.findThresholdCandidates()).thenThrow(new IllegalStateException("database gone"));
        var task = new EventThresholdChecker(
                        events,
                        mock(EventRegistrationRepository.class),
                        mock(EventCancellationService.class),
                        mock(OccurrenceCalendar.class),
                        mock(StationReadOnlyGuard.class))
                .scheduledTasks()
                .getFirst();

        assertDoesNotThrow(task.work()::run);
    }

    @Test
    void deadlineCheckRunsEveryFiveMinutes() {
        assertTask(
                taskOf(mock(RegistrationDeadlineChecker.class)),
                "registration-deadline-check",
                Duration.ofMinutes(5),
                Duration.ofMinutes(5));
    }

    @Test
    void fieldRegistrationSweepRunsTwiceADay() {
        assertTask(
                taskOf(mock(FieldRegistrationSweeper.class)),
                "field-registration-sweep",
                Duration.ofMinutes(2),
                Duration.ofHours(12));
    }

    @Test
    void settledRefusalSweepRunsTheSweep() {
        var sweeper = mock(SettledRefusalSweeper.class);
        var task = taskOf(sweeper);

        task.work().run();

        verify(sweeper).sweep();
        assertTask(task, "settled-refusal-sweep", Duration.ofMinutes(1), Duration.ofMinutes(5));
    }

    private static ScheduledTask taskOf(TaskSource mockedSource) {
        when(mockedSource.scheduledTasks()).thenCallRealMethod();
        return mockedSource.scheduledTasks().getFirst();
    }

    private static void assertTask(ScheduledTask task, String name, Duration initialDelay, Duration period) {
        assertEquals(name, task.name());
        assertEquals(Schedule.fixedDelay(initialDelay, period), task.schedule());
    }
}
