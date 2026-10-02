/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.lifecycle.Schedule;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoardTasksTest {

    @Test
    void theDueDateCheckRunsEveryHour() {
        var checker = mock(DueDateReminderChecker.class);
        when(checker.scheduledTasks()).thenCallRealMethod();
        var task = checker.scheduledTasks().getFirst();

        assertEquals("board-due-date-check", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofMinutes(1), Duration.ofHours(1)), task.schedule());
    }
}
