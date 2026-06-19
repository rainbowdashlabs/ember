/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.federation.service.FederationReplayCache;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederationReplayCacheTest {

    @Test
    void firstUseIsAcceptedSecondIsRejected() {
        var cache = new FederationReplayCache();
        UUID nonce = UUID.randomUUID();

        assertTrue(cache.checkAndRemember(1, nonce));
        assertFalse(cache.checkAndRemember(1, nonce));
    }

    @Test
    void sameNonceAcrossDifferentPartnersIsAccepted() {
        var cache = new FederationReplayCache();
        UUID nonce = UUID.randomUUID();

        assertTrue(cache.checkAndRemember(1, nonce));
        assertTrue(cache.checkAndRemember(2, nonce));
    }

    @Test
    void differentNoncesForSamePartnerAreAccepted() {
        var cache = new FederationReplayCache();

        assertTrue(cache.checkAndRemember(1, UUID.randomUUID()));
        assertTrue(cache.checkAndRemember(1, UUID.randomUUID()));
        assertTrue(cache.checkAndRemember(1, UUID.randomUUID()));
    }
}
