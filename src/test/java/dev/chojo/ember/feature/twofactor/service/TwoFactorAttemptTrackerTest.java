/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TwoFactorAttemptTrackerTest {

    @Test
    void countsFailuresPerKey() {
        var tracker = new TwoFactorAttemptTracker();
        assertEquals(1, tracker.recordFailure("a"));
        assertEquals(2, tracker.recordFailure("a"));
        assertEquals(1, tracker.recordFailure("b"));
    }

    @Test
    void reachesLimitAfterMaxAttempts() {
        var tracker = new TwoFactorAttemptTracker();
        int last = 0;
        for (int i = 0; i < TwoFactorAttemptTracker.MAX_ATTEMPTS; i++) {
            last = tracker.recordFailure("token");
        }
        assertEquals(TwoFactorAttemptTracker.MAX_ATTEMPTS, last);
    }

    @Test
    void resetClearsCount() {
        var tracker = new TwoFactorAttemptTracker();
        tracker.recordFailure("token");
        tracker.recordFailure("token");
        tracker.reset("token");
        assertEquals(1, tracker.recordFailure("token"));
    }
}
