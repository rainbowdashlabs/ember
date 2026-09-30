/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import dev.chojo.ember.lifecycle.Schedule;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WaitingListTasksTest {

    @Test
    void confirmationCheckRunsOnceADay() {
        var service = mock(WaitingListService.class);
        when(service.scheduledTasks()).thenCallRealMethod();
        var task = service.scheduledTasks().getFirst();

        assertEquals("waiting-list-confirmation-check", task.name());
        assertEquals(Schedule.fixedRate(Duration.ofHours(1), Duration.ofHours(24)), task.schedule());
    }
}
