/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.conf.file.elements.Mailing;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationTasksTest {

    private static Mailing digestEvery(int minutes) {
        var mailing = mock(Mailing.class);
        when(mailing.notificationDigestIntervalMinutes()).thenReturn(minutes);
        return mailing;
    }

    @Test
    void theSweepLooksInAsOftenAsAShortDigestInterval() {
        assertEquals(Duration.ofMinutes(5), NotificationDigest.sweepInterval(digestEvery(5)));
    }

    @Test
    void theSweepLooksInEveryFifteenMinutesOtherwise() {
        assertEquals(Duration.ofMinutes(15), NotificationDigest.sweepInterval(digestEvery(240)));
        assertEquals(Duration.ofMinutes(15), NotificationDigest.sweepInterval(digestEvery(0)));
    }

    @Test
    void theTaskRunsTheSweep() {
        var digest = mock(NotificationDigest.class);
        var task = new NotificationDigest.Task(digest, digestEvery(60));

        task.run();

        verify(digest).sweep(any());
        assertEquals("notification-sweep", task.name());
        assertEquals(Duration.ofMinutes(15), task.schedule().period());
    }
}
