/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What may and may not shut a provider out of a whole domain.
 *
 * <p>The cost of being wrong is not symmetric. Missing a block wastes one send; inventing one takes
 * a working provider away from every address at that domain for a week. So the cases that must be
 * refused are worth more tests than the ones that must be caught.
 */
class RelayBlockDetectorTest {

    @Test
    void aRefusalNamingTheSenderIpIsARelayBlock() {
        assertTrue(
                RelayBlockDetector.blamesTheRelay(
                        "550-Sophos Anti Spam Engine has blocked this Email because the sender IP 550 Address is blacklisted."));
    }

    @Test
    void theUsualWordingsAreCaught() {
        assertTrue(RelayBlockDetector.blamesTheRelay("554 5.7.1 Service unavailable; Client host blocked using dnsbl"));
        assertTrue(RelayBlockDetector.blamesTheRelay("Your IP address is on our blocklist"));
        assertTrue(RelayBlockDetector.blamesTheRelay("Sender address banned by policy"));
    }

    /**
     * A full mailbox or a rejected message says nothing about the relay, and a provider taken away
     * over one of these would be taken away for everybody at that domain.
     */
    @Test
    void aRefusalAboutTheReaderOrTheMessageIsNotARelayBlock() {
        assertFalse(RelayBlockDetector.blamesTheRelay("452 4.2.2 Mailbox full"));
        assertFalse(RelayBlockDetector.blamesTheRelay("550 5.1.1 User unknown"));
        assertFalse(RelayBlockDetector.blamesTheRelay("Message content rejected as spam"));
        assertFalse(RelayBlockDetector.blamesTheRelay("Greylisted, please try again later"));
        assertFalse(RelayBlockDetector.blamesTheRelay("Recipient address rejected: access denied"));
    }

    /**
     * Naming a block without saying what is blocked is not enough: half the refusals in the world
     * contain the word.
     */
    @Test
    void aBlockWithoutASenderIsNotEnough() {
        assertFalse(RelayBlockDetector.blamesTheRelay("blocked"));
        assertFalse(RelayBlockDetector.blamesTheRelay("This message was blocked"));
    }

    @Test
    void nothingAtAllIsNotABlock() {
        assertFalse(RelayBlockDetector.blamesTheRelay(null));
        assertFalse(RelayBlockDetector.blamesTheRelay(""));
        assertFalse(RelayBlockDetector.blamesTheRelay("   "));
    }
}
