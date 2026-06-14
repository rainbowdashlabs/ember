/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.discovery.entity.PeerSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveryStationCacheRepositoryTest extends RepositoryTestBase {

    private static DiscoveryStationCard card(String uid, String name) {
        return new DiscoveryStationCard(
                uid,
                name,
                "slogan",
                "https://logo",
                "DE",
                "Bayern",
                "Town",
                "https://contact",
                List.of("fire"),
                "<10",
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void upsertAndFindForPeer() {
        discoveryPeerRepo.upsert("k-cache-1", "https://c1.example", "fp-c1", PeerSource.MANUAL, null);
        var now = Instant.now();
        discoveryStationCacheRepo.upsert("k-cache-1", card("uid-1", "Foo"), now);
        discoveryStationCacheRepo.upsert("k-cache-1", card("uid-2", "Bar"), now);

        var rows = discoveryStationCacheRepo.findForPeer("k-cache-1");
        assertEquals(2, rows.size());
        assertEquals(
                "Foo",
                rows.stream()
                        .filter(r -> r.stationUid().equals("uid-1"))
                        .findFirst()
                        .orElseThrow()
                        .card()
                        .name());
    }

    @Test
    void upsertRefreshesPayload() {
        discoveryPeerRepo.upsert("k-cache-up", "https://cu.example", "fp-cu", PeerSource.MANUAL, null);
        var now = Instant.now();
        discoveryStationCacheRepo.upsert("k-cache-up", card("uid-r", "Original"), now);
        discoveryStationCacheRepo.upsert("k-cache-up", card("uid-r", "Renamed"), now.plusSeconds(1));
        var fresh = discoveryStationCacheRepo.findForPeer("k-cache-up").stream()
                .filter(r -> r.stationUid().equals("uid-r"))
                .findFirst()
                .orElseThrow();
        assertEquals("Renamed", fresh.card().name());
    }

    @Test
    void deleteMissingClearsAllWhenListEmpty() {
        discoveryPeerRepo.upsert("k-cache-empty", "https://ce.example", "fp-ce", PeerSource.MANUAL, null);
        discoveryStationCacheRepo.upsert("k-cache-empty", card("uid-1", "A"), Instant.now());
        int removed = discoveryStationCacheRepo.deleteMissing("k-cache-empty", List.of());
        assertEquals(1, removed);
        assertTrue(discoveryStationCacheRepo.findForPeer("k-cache-empty").isEmpty());
    }

    @Test
    void deleteMissingKeepsPresent() {
        discoveryPeerRepo.upsert("k-cache-keep", "https://ck.example", "fp-ck", PeerSource.MANUAL, null);
        discoveryStationCacheRepo.upsert("k-cache-keep", card("uid-1", "A"), Instant.now());
        discoveryStationCacheRepo.upsert("k-cache-keep", card("uid-2", "B"), Instant.now());
        discoveryStationCacheRepo.upsert("k-cache-keep", card("uid-3", "C"), Instant.now());
        int removed = discoveryStationCacheRepo.deleteMissing("k-cache-keep", List.of("uid-1", "uid-2"));
        assertEquals(1, removed);
        var remaining = discoveryStationCacheRepo.findForPeer("k-cache-keep").stream()
                .map(r -> r.stationUid())
                .toList();
        assertTrue(remaining.contains("uid-1"));
        assertTrue(remaining.contains("uid-2"));
        assertFalse(remaining.contains("uid-3"));
    }

    @Test
    void findAllOnlyReturnsReachableAndUnblocked() {
        discoveryPeerRepo.upsert("k-cache-all-ok", "https://ok.example", "fp-ok", PeerSource.MANUAL, null);
        discoveryPeerRepo.upsert("k-cache-all-bad", "https://bad.example", "fp-bad", PeerSource.MANUAL, null);
        discoveryPeerRepo.setBlocked("k-cache-all-bad", true);

        discoveryStationCacheRepo.upsert("k-cache-all-ok", card("uid-vis", "Visible"), Instant.now());
        discoveryStationCacheRepo.upsert("k-cache-all-bad", card("uid-hid", "Hidden"), Instant.now());

        var all = discoveryStationCacheRepo.findAll();
        var uids = all.stream().map(r -> r.stationUid()).toList();
        assertTrue(uids.contains("uid-vis"));
        assertFalse(uids.contains("uid-hid"));
    }
}
