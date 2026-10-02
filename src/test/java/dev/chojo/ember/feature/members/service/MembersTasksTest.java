/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.lifecycle.Schedule;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class MembersTasksTest {

    @Test
    void theExpiryReminderSweepRunsEveryThirtyMinutes() {
        var service = spy(new ExpiryReminderService(null, null, null, null, null, null, null, null, null));
        doNothing().when(service).sweep(any());
        var task = service.scheduledTasks().getFirst();

        task.work().run();

        verify(service).sweep(any());
        assertEquals("expiry-reminder-check", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(30)), task.schedule());
    }
}
