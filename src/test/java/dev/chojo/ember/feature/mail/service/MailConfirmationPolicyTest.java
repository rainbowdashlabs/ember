/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Whether a confirmation asked for by mail is worth asking for. */
class MailConfirmationPolicyTest {

    private static final MailChainEntry ONE_PROVIDER = new MailChainEntry(
            0,
            MailProviderType.SMTP,
            "mail.test",
            25,
            false,
            "user",
            "pass",
            "",
            "ember@test.com",
            "Ember",
            1,
            200,
            "",
            "");

    private static MailConfirmationPolicy policy(List<MailChainEntry> chain, boolean demoMode) {
        var chainService = mock(MailChainService.class);
        when(chainService.forInstance()).thenReturn(chain);
        var demo = mock(Demo.class);
        when(demo.enabled()).thenReturn(demoMode);
        return new MailConfirmationPolicy(chainService, demo);
    }

    @Test
    void nothingToSendWithMeansTheAnswerIsAlreadyGiven() {
        assertTrue(policy(List.of(), false).confirmationCountsAsGranted());
    }

    @Test
    void aProviderMeansTheQuestionIsWorthAsking() {
        assertFalse(policy(List.of(ONE_PROVIDER), false).confirmationCountsAsGranted());
    }

    /** A demo swallows its post on purpose, which is not the same as having nowhere to post it. */
    @Test
    void aDemoStillAsks() {
        assertFalse(policy(List.of(), true).confirmationCountsAsGranted());
    }
}
