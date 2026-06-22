/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.repository;

import dev.chojo.ember.feature.discovery.entity.DiscoveryPeer;
import dev.chojo.ember.feature.discovery.entity.PeerSource;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DiscoveryPeerRepositoryTest extends RepositoryTestBase {

    @Test
    void upsertCreatesAndRefreshes() {
        var peer = discoveryPeerRepo.upsert("k-upsert", "https://a.example", "fp-a", PeerSource.MANUAL, null);
        assertEquals("k-upsert", peer.publicKey());
        assertEquals("https://a.example", peer.baseUrl());
        assertEquals(PeerSource.MANUAL, peer.source());
        assertTrue(peer.reachable());
        assertFalse(peer.blocked());

        // Second upsert refreshes baseUrl + last_seen_at, keeps source.
        var refreshed = discoveryPeerRepo.upsert("k-upsert", "https://a2.example", "fp-a", PeerSource.GOSSIP, "intro");
        assertEquals("https://a2.example", refreshed.baseUrl());
    }

    @Test
    void findByPublicKeyAndBaseUrl() {
        discoveryPeerRepo.upsert("k-find", "https://find.example", "fp-find", PeerSource.MANUAL, null);
        assertTrue(discoveryPeerRepo.findByPublicKey("k-find").isPresent());
        assertTrue(discoveryPeerRepo.findByPublicKey("missing").isEmpty());
        assertTrue(discoveryPeerRepo.findByBaseUrl("https://find.example").isPresent());
        assertTrue(discoveryPeerRepo.findByBaseUrl("https://missing.example").isEmpty());
    }

    @Test
    void markPingedReachedUnreachable() {
        discoveryPeerRepo.upsert("k-mark", "https://mark.example", "fp-mark", PeerSource.MANUAL, null);

        var when = Instant.now();
        discoveryPeerRepo.markPinged("k-mark", when);
        var afterPing = discoveryPeerRepo.findByPublicKey("k-mark").orElseThrow();
        assertNotNull(afterPing.lastPingedAt());

        discoveryPeerRepo.markReached("k-mark", when);
        var afterReached = discoveryPeerRepo.findByPublicKey("k-mark").orElseThrow();
        assertNotNull(afterReached.lastReachedAt());
        assertTrue(afterReached.reachable());

        discoveryPeerRepo.markUnreachable("k-mark");
        assertFalse(discoveryPeerRepo.findByPublicKey("k-mark").orElseThrow().reachable());
    }

    @Test
    void reputationAndDecay() {
        discoveryPeerRepo.upsert("k-rep", "https://rep.example", "fp-rep", PeerSource.MANUAL, null);
        discoveryPeerRepo.addReputation("k-rep", -10);
        assertEquals(
                -10, discoveryPeerRepo.findByPublicKey("k-rep").orElseThrow().reputation());

        // Decay should pull -10 toward 0 by 5.
        int decayed = discoveryPeerRepo.decayReputation(5);
        assertTrue(decayed >= 1);
        assertEquals(
                -5, discoveryPeerRepo.findByPublicKey("k-rep").orElseThrow().reputation());

        // Reputation is capped at 0 by decay.
        discoveryPeerRepo.decayReputation(20);
        assertEquals(0, discoveryPeerRepo.findByPublicKey("k-rep").orElseThrow().reputation());
    }

    @Test
    void blockedFlag() {
        discoveryPeerRepo.upsert("k-block", "https://block.example", "fp-block", PeerSource.MANUAL, null);
        discoveryPeerRepo.setBlocked("k-block", true);
        assertTrue(discoveryPeerRepo.findByPublicKey("k-block").orElseThrow().blocked());
        discoveryPeerRepo.setBlocked("k-block", false);
        assertFalse(discoveryPeerRepo.findByPublicKey("k-block").orElseThrow().blocked());
    }

    @Test
    void usableExcludesBlockedAndDeepNegative() {
        discoveryPeerRepo.upsert("k-ok", "https://ok.example", "fp-ok", PeerSource.MANUAL, null);
        var blocked = discoveryPeerRepo.upsert("k-blk", "https://blk.example", "fp-blk", PeerSource.MANUAL, null);
        discoveryPeerRepo.setBlocked(blocked.publicKey(), true);
        discoveryPeerRepo.upsert("k-bad", "https://bad.example", "fp-bad", PeerSource.MANUAL, null);
        discoveryPeerRepo.addReputation("k-bad", -100);

        List<String> usableKeys = discoveryPeerRepo.findUsable().stream()
                .map(DiscoveryPeer::publicKey)
                .toList();
        assertTrue(usableKeys.contains("k-ok"));
        assertFalse(usableKeys.contains("k-blk"));
        assertFalse(usableKeys.contains("k-bad"));
    }

    @Test
    void reachableFiltersBlockedAndUnreachable() {
        discoveryPeerRepo.upsert("k-rch-1", "https://rch1.example", "fp-rch1", PeerSource.MANUAL, null);
        var ur = discoveryPeerRepo.upsert("k-rch-2", "https://rch2.example", "fp-rch2", PeerSource.MANUAL, null);
        discoveryPeerRepo.markUnreachable(ur.publicKey());

        List<String> reachable = discoveryPeerRepo.findReachable().stream()
                .map(DiscoveryPeer::publicKey)
                .toList();
        assertTrue(reachable.contains("k-rch-1"));
        assertFalse(reachable.contains("k-rch-2"));
    }

    @Test
    void findAllReturnsEverything() {
        discoveryPeerRepo.upsert("k-all-1", "https://all1.example", "fp-all1", PeerSource.MANUAL, null);
        discoveryPeerRepo.upsert("k-all-2", "https://all2.example", "fp-all2", PeerSource.MANUAL, null);
        assertTrue(discoveryPeerRepo.findAll().size() >= 2);
    }

    @Test
    void deleteRemoves() {
        discoveryPeerRepo.upsert("k-del", "https://del.example", "fp-del", PeerSource.MANUAL, null);
        assertTrue(discoveryPeerRepo.delete("k-del"));
        assertFalse(discoveryPeerRepo.delete("k-del"));
        assertTrue(discoveryPeerRepo.findByPublicKey("k-del").isEmpty());
    }

    @Test
    void findManyByKeysIncludesEmpty() {
        assertTrue(discoveryPeerRepo.findManyByKeys(List.of()).isEmpty());
        discoveryPeerRepo.upsert("k-many-1", "https://m1.example", "fp-m1", PeerSource.MANUAL, null);
        discoveryPeerRepo.upsert("k-many-2", "https://m2.example", "fp-m2", PeerSource.MANUAL, null);
        var found = discoveryPeerRepo.findManyByKeys(List.of("k-many-1", "k-many-2", "missing"));
        assertEquals(2, found.size());
    }
}
