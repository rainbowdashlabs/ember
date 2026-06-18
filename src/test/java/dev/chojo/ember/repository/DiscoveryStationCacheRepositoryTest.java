/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.discovery.entity.CachedDiscoveryStation;
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
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                null);
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
                .map(CachedDiscoveryStation::stationUid)
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
        var uids = all.stream().map(CachedDiscoveryStation::stationUid).toList();
        assertTrue(uids.contains("uid-vis"));
        assertFalse(uids.contains("uid-hid"));
    }

    @Test
    void findByStationUidsReturnsMatches() {
        discoveryPeerRepo.upsert("k-cache-uids-ok", "https://uids.example", "fp-uids", PeerSource.MANUAL, null);
        discoveryStationCacheRepo.upsert("k-cache-uids-ok", card("uid-by-1", "Alpha"), Instant.now());
        discoveryStationCacheRepo.upsert("k-cache-uids-ok", card("uid-by-2", "Beta"), Instant.now());

        var matches = discoveryStationCacheRepo.findByStationUids(List.of("uid-by-1", "uid-by-2", "uid-missing"));
        var uids = matches.stream().map(CachedDiscoveryStation::stationUid).toList();
        assertTrue(uids.contains("uid-by-1"));
        assertTrue(uids.contains("uid-by-2"));
        assertFalse(uids.contains("uid-missing"));

        assertTrue(discoveryStationCacheRepo.findByStationUids(List.of()).isEmpty());
        assertTrue(discoveryStationCacheRepo.findByStationUids(null).isEmpty());
    }

    @Test
    void findByStationUidsExcludesBlockedPeers() {
        discoveryPeerRepo.upsert("k-cache-uids-bad", "https://uidsbad.example", "fp-uidsbad", PeerSource.MANUAL, null);
        discoveryPeerRepo.setBlocked("k-cache-uids-bad", true);
        discoveryStationCacheRepo.upsert("k-cache-uids-bad", card("uid-blocked", "Blocked"), Instant.now());

        var matches = discoveryStationCacheRepo.findByStationUids(List.of("uid-blocked"));
        assertTrue(matches.isEmpty());
    }

    @Test
    void searchForPickerEmptyTermReturnsRecent() {
        discoveryPeerRepo.upsert("k-cache-pick-1", "https://p1.example", "fp-p1", PeerSource.MANUAL, null);
        discoveryStationCacheRepo.upsert("k-cache-pick-1", card("uid-pk-1", "Pickable"), Instant.now());

        var result = discoveryStationCacheRepo.searchForPicker(null, 10);
        var uids = result.stream().map(CachedDiscoveryStation::stationUid).toList();
        assertTrue(uids.contains("uid-pk-1"));

        var blank = discoveryStationCacheRepo.searchForPicker("   ", 10);
        var blankUids = blank.stream().map(CachedDiscoveryStation::stationUid).toList();
        assertTrue(blankUids.contains("uid-pk-1"));
    }

    @Test
    void searchForPickerMatchesNameCityCountry() {
        discoveryPeerRepo.upsert("k-cache-pick-2", "https://p2.example", "fp-p2", PeerSource.MANUAL, null);
        discoveryStationCacheRepo.upsert("k-cache-pick-2", card("uid-pk-2", "Unique-Pickname"), Instant.now());

        var byName = discoveryStationCacheRepo.searchForPicker("unique-pickname", 10);
        assertTrue(byName.stream().anyMatch(r -> r.stationUid().equals("uid-pk-2")));

        var byCity = discoveryStationCacheRepo.searchForPicker("town", 10);
        assertTrue(byCity.stream().anyMatch(r -> r.stationUid().equals("uid-pk-2")));

        var byCountry = discoveryStationCacheRepo.searchForPicker("de", 10);
        assertTrue(byCountry.stream().anyMatch(r -> r.stationUid().equals("uid-pk-2")));

        var miss = discoveryStationCacheRepo.searchForPicker("not-going-to-match-anything", 10);
        assertTrue(miss.stream().noneMatch(r -> r.stationUid().equals("uid-pk-2")));
    }
}
