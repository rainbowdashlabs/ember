/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupMailTest {

    @Test
    void anUnansweredQuestionSendsAtOnce() {
        assertEquals(SetupMail.SEND_NOW, SetupMail.of(null));
    }

    @Test
    void askingForItSendsAtOnce() {
        assertEquals(SetupMail.SEND_NOW, SetupMail.of(true));
    }

    @Test
    void sayingNoHoldsItBack() {
        assertEquals(SetupMail.LATER, SetupMail.of(false));
    }

    @Test
    void onlySendNowOwesAMailRightAway() {
        assertTrue(SetupMail.SEND_NOW.sendsNow());
        assertFalse(SetupMail.LATER.sendsNow());
    }
}
