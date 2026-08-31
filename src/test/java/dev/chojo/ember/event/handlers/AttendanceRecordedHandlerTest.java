/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.events.AttendanceRecorded;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** An evening somebody turned up to counts towards their trial period. */
class AttendanceRecordedHandlerTest {

    @Test
    void itHandlesItsOwnEvent() {
        assertEquals(
                AttendanceRecorded.class, new AttendanceRecordedHandler(mock(WaitingListService.class)).eventType());
    }

    @Test
    void theMemberWhoWasThereHasTheEveningCounted() {
        var waitingListService = mock(WaitingListService.class);

        new AttendanceRecordedHandler(waitingListService).handle(new AttendanceRecorded(3, 42, 8));

        verify(waitingListService).recordTrialAttendance(42);
    }
}
