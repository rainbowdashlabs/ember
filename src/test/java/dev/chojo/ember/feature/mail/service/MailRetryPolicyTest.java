/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MailRetryPolicyTest {

    @Test
    void aProviderWithAttemptsLeftTriesAgain() {
        assertEquals(MailRetryPolicy.Step.RETRY_SAME_PROVIDER, MailRetryPolicy.after(1, 2, 0, 1));
    }

    @Test
    void aProviderOutOfAttemptsHandsOverToTheNext() {
        assertEquals(MailRetryPolicy.Step.NEXT_PROVIDER, MailRetryPolicy.after(2, 2, 0, 2));
    }

    @Test
    void theLastProviderOutOfAttemptsGivesUp() {
        assertEquals(MailRetryPolicy.Step.GIVE_UP, MailRetryPolicy.after(2, 2, 1, 2));
        assertEquals(MailRetryPolicy.Step.GIVE_UP, MailRetryPolicy.after(1, 1, 0, 1));
    }

    @Test
    void anEmptyChainGivesUp() {
        assertEquals(MailRetryPolicy.Step.GIVE_UP, MailRetryPolicy.after(1, 1, 0, 0));
    }

    @Test
    void theDelayDoublesWithEveryAttempt() {
        assertEquals(Duration.ofSeconds(30), MailRetryPolicy.delayAfter(1));
        assertEquals(Duration.ofSeconds(60), MailRetryPolicy.delayAfter(2));
        assertEquals(Duration.ofSeconds(120), MailRetryPolicy.delayAfter(3));
    }

    @Test
    void theDelayStopsGrowingAtTheCap() {
        assertEquals(MailRetryPolicy.MAX_DELAY, MailRetryPolicy.delayAfter(8));
        assertEquals(MailRetryPolicy.MAX_DELAY, MailRetryPolicy.delayAfter(1_000));
    }

    @Test
    void aDelayIsNeverShorterThanTheFirst() {
        assertEquals(MailRetryPolicy.FIRST_DELAY, MailRetryPolicy.delayAfter(0));
    }
}
