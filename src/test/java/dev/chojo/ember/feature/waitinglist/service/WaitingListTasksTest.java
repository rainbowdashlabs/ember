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

class WaitingListTasksTest {

    @Test
    void confirmationCheckRunsOnceADay() {
        var task = new WaitingListService.ConfirmationTask(mock(WaitingListService.class));

        assertEquals("waiting-list-confirmation-check", task.name());
        assertEquals(Schedule.fixedRate(Duration.ofHours(1), Duration.ofHours(24)), task.schedule());
    }
}
