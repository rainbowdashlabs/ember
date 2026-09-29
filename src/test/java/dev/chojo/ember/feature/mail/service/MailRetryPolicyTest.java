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

    private static final Duration JUST_QUEUED = Duration.ofSeconds(5);
    private static final Duration PAST_THE_WINDOW = MailRetryPolicy.MIN_RETRY_WINDOW.plusMinutes(1);

    @Test
    void aProviderWithAttemptsLeftTriesAgain() {
        assertEquals(MailRetryPolicy.Step.RETRY_SAME_PROVIDER, MailRetryPolicy.after(1, 2, 0, 1, JUST_QUEUED));
    }

    @Test
    void aProviderOutOfAttemptsHandsOverToTheNext() {
        assertEquals(MailRetryPolicy.Step.NEXT_PROVIDER, MailRetryPolicy.after(2, 2, 0, 2, JUST_QUEUED));
        assertEquals(MailRetryPolicy.Step.NEXT_PROVIDER, MailRetryPolicy.after(2, 2, 0, 2, PAST_THE_WINDOW));
    }

    @Test
    void theLastProviderKeepsTryingWithinTheWindow() {
        assertEquals(
                MailRetryPolicy.Step.RETRY_SAME_PROVIDER,
                MailRetryPolicy.after(2, 2, 0, 1, Duration.ofMinutes(10)),
                "one provider with two attempts, failing for ten minutes, is still pending");
        assertEquals(
                MailRetryPolicy.Step.RETRY_SAME_PROVIDER, MailRetryPolicy.after(6, 2, 1, 2, Duration.ofMinutes(59)));
    }

    @Test
    void theLastProviderGivesUpOnceTheWindowHasPassed() {
        assertEquals(MailRetryPolicy.Step.GIVE_UP, MailRetryPolicy.after(2, 2, 0, 1, PAST_THE_WINDOW));
        assertEquals(MailRetryPolicy.Step.GIVE_UP, MailRetryPolicy.after(1, 1, 1, 2, PAST_THE_WINDOW));
        assertEquals(MailRetryPolicy.Step.GIVE_UP, MailRetryPolicy.after(1, 1, 0, 1, MailRetryPolicy.MIN_RETRY_WINDOW));
    }

    @Test
    void anEmptyChainGivesUpOnceTheWindowHasPassed() {
        assertEquals(MailRetryPolicy.Step.GIVE_UP, MailRetryPolicy.after(1, 1, 0, 0, PAST_THE_WINDOW));
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
