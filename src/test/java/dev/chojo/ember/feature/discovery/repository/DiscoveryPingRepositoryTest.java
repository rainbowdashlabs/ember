/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.repository;

import dev.chojo.ember.feature.discovery.entity.PingDirection;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveryPingRepositoryTest extends RepositoryTestBase {

    @Test
    void recordIsIdempotentOnConflict() {
        var now = Instant.now();
        boolean fresh = discoveryPingRepo.record("nonce-1", PingDirection.OUT, "k-1", now, now.plusSeconds(60));
        assertTrue(fresh);
        boolean replay = discoveryPingRepo.record("nonce-1", PingDirection.IN, "k-1", now, now.plusSeconds(60));
        assertFalse(replay);
    }

    @Test
    void findByNonce() {
        var now = Instant.now();
        discoveryPingRepo.record("nonce-find", PingDirection.OUT, "k-f", now, now.plusSeconds(60));
        var hit = discoveryPingRepo.findByNonce("nonce-find").orElseThrow();
        assertEquals(PingDirection.OUT, hit.direction());
        assertEquals("k-f", hit.peerKey());
        assertTrue(discoveryPingRepo.findByNonce("missing").isEmpty());
    }

    @Test
    void deleteExpired() {
        var past = Instant.now().minusSeconds(120);
        discoveryPingRepo.record("nonce-expired-1", PingDirection.OUT, "k-x1", past, past.plusSeconds(1));
        discoveryPingRepo.record("nonce-expired-2", PingDirection.IN, "k-x2", past, past.plusSeconds(2));
        var future = Instant.now();
        discoveryPingRepo.record("nonce-fresh", PingDirection.OUT, "k-fresh", future, future.plusSeconds(120));

        int removed = discoveryPingRepo.deleteExpired();
        assertTrue(removed >= 2);
        assertTrue(discoveryPingRepo.findByNonce("nonce-fresh").isPresent());
        assertTrue(discoveryPingRepo.findByNonce("nonce-expired-1").isEmpty());
    }
}
