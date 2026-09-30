/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The sweep itself, driven directly: the timer behind it only fires once a minute, which no test
 * waits for.
 */
class ManagedLoginNoticeSweeperTest {

    @Test
    void aSweepSendsWhatIsDue() {
        var noticeService = mock(ManagedLoginNoticeService.class);

        new ManagedLoginNoticeSweeper(noticeService).sweep();

        verify(noticeService).dispatch();
    }

    @Test
    void theTaskSweepsEveryMinute() {
        var noticeService = mock(ManagedLoginNoticeService.class);
        var task = new ManagedLoginNoticeSweeper.Task(new ManagedLoginNoticeSweeper(noticeService));

        task.run();

        verify(noticeService).dispatch();
        assertEquals("managed-login-notice-sweep", task.name());
        assertEquals(Duration.ofMinutes(1), task.schedule().period());
    }

    @Test
    void aFailedSweepIsSwallowedSoTheTimerKeepsRunning() {
        var noticeService = mock(ManagedLoginNoticeService.class);
        doThrow(new RuntimeException("db unreachable")).when(noticeService).dispatch();

        new ManagedLoginNoticeSweeper(noticeService).sweep();

        verify(noticeService).dispatch();
    }
}
