/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth.signing;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nonces held in memory: taken once per sender, never forgotten while their window is open, and
 * refused rather than forgotten when the store is full.
 */
class MemoryReplayStoreTest {
    private static final Instant WINDOW = Instant.now().plus(Duration.ofMinutes(10));

    private static String nonce() {
        return UUID.randomUUID().toString();
    }

    @Test
    void firstUseIsAcceptedSecondIsRejected() {
        var store = new MemoryReplayStore();
        String nonce = nonce();

        assertTrue(store.firstSighting("partner:1", nonce, WINDOW));
        assertFalse(store.firstSighting("partner:1", nonce, WINDOW));
    }

    @Test
    void sameNonceFromDifferentSendersIsAccepted() {
        var store = new MemoryReplayStore();
        String nonce = nonce();

        assertTrue(store.firstSighting("partner:1", nonce, WINDOW));
        assertTrue(store.firstSighting("partner:2", nonce, WINDOW));
    }

    @Test
    void differentNoncesFromOneSenderAreAccepted() {
        var store = new MemoryReplayStore();

        assertTrue(store.firstSighting("partner:1", nonce(), WINDOW));
        assertTrue(store.firstSighting("partner:1", nonce(), WINDOW));
        assertTrue(store.firstSighting("partner:1", nonce(), WINDOW));
    }

    /**
     * Many copies of one captured request arriving at the same moment: exactly one gets through.
     */
    @RepeatedTest(20)
    void concurrentCopiesOfOneRequestPassOnlyOnce() throws Exception {
        var store = new MemoryReplayStore();
        String nonce = nonce();
        int copies = 32;
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(copies)) {
            var attempts = IntStream.range(0, copies)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return store.firstSighting("partner:1", nonce, WINDOW);
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
     * A full store refuses new requests rather than forgetting a nonce whose window is still open.
     */
    @Test
    void aFullStoreRefusesInsteadOfForgetting() {
        var store = new MemoryReplayStore(3);
        String first = nonce();
        assertTrue(store.firstSighting("partner:1", first, WINDOW));
        assertTrue(store.firstSighting("partner:1", nonce(), WINDOW));
        assertTrue(store.firstSighting("partner:1", nonce(), WINDOW));

        assertFalse(store.firstSighting("partner:1", nonce(), WINDOW));
        assertFalse(store.firstSighting("partner:1", first, WINDOW));
    }

    @Test
    void aNonceWhoseWindowHasPassedIsForgotten() {
        var store = new MemoryReplayStore(1);
        assertTrue(store.firstSighting("partner:1", nonce(), Instant.now().minusSeconds(1)));

        assertTrue(store.firstSighting("partner:1", nonce(), WINDOW));
    }
}
