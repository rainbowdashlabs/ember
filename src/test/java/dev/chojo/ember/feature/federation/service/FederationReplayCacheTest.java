/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /**
     * Many copies of one captured request arriving at the same moment: exactly one gets through.
     */
    @RepeatedTest(20)
    void concurrentCopiesOfOneRequestPassOnlyOnce() throws Exception {
        var cache = new FederationReplayCache();
        UUID nonce = UUID.randomUUID();
        int copies = 32;
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(copies)) {
            var attempts = IntStream.range(0, copies)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return cache.checkAndRemember(1, nonce);
                    }))
                    .toList();
            start.countDown();
            long accepted = 0;
            for (var attempt : attempts) {
                if (attempt.get()) accepted++;
            }
            assertEquals(1, accepted);
        }
    }

    /**
     * A full cache refuses new requests rather than forgetting a nonce whose window is still open.
     */
    @Test
    void aFullCacheRefusesInsteadOfForgetting() {
        var cache = new FederationReplayCache(3);
        UUID first = UUID.randomUUID();
        assertTrue(cache.checkAndRemember(1, first));
        assertTrue(cache.checkAndRemember(1, UUID.randomUUID()));
        assertTrue(cache.checkAndRemember(1, UUID.randomUUID()));

        assertFalse(cache.checkAndRemember(1, UUID.randomUUID()));
        assertFalse(cache.checkAndRemember(1, first));
    }
}
