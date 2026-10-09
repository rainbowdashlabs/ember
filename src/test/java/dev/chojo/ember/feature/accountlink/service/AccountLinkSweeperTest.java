/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The expiry sweep keeps its timer running when a run fails. */
class AccountLinkSweeperTest {

    @Test
    void aFailedSweepIsSwallowedSoTheTimerKeepsRunning() {
        var links = mock(AccountLinkService.class);
        doThrow(new RuntimeException("db unreachable")).when(links).expireOverdue();

        new AccountLinkSweeper(links).sweep();

        verify(links).expireOverdue();
    }
}
