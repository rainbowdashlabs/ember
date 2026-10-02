/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.discovery.entity.PublishedRemoteStation;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryIdentity;
import dev.chojo.ember.feature.discovery.repository.DiscoveryStationCacheRepository;
import dev.chojo.ember.feature.discovery.service.RemoteStationListingService.RemoteStation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the public discovery page is handed about the stations of other instances, and where it links them.
 */
class RemoteStationListingServiceTest {
    private static final UUID STATION = UUID.fromString("7f0c1f5e-3f4c-4b6f-9a51-0d1e2f3a4b5c");
    private static final String OWN_KEY = "own-key";
    private static final String OWN_URL = "https://self.example";

    private DiscoveryStationCacheRepository cache;
    private RemoteStationListingService service;

    @BeforeEach
    void setup() {
        cache = mock(DiscoveryStationCacheRepository.class);
        var ping = mock(DiscoveryPingService.class);
        when(ping.selfIdentity()).thenReturn(new DiscoveryIdentity(OWN_URL, OWN_KEY, "own-id"));
        service = new RemoteStationListingService(cache, ping);
    }

    private static DiscoveryStationCard card(String uid, String slug) {
        return new DiscoveryStationCard(
                uid,
                "Wache Nord",
                "Wir sind da",
                "https://elsewhere.example/logo",
                "DE",
                null,
                "Nordstadt",
                "https://elsewhere.example/anything",
                List.of(),
                "<10",
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                slug);
    }

    private static PublishedRemoteStation published(String baseUrl, DiscoveryStationCard card) {
        return new PublishedRemoteStation("k", baseUrl, card, false);
    }

    private List<RemoteStation> listing(PublishedRemoteStation... stations) {
        when(cache.findPublishedElsewhere(OWN_KEY, OWN_URL)).thenReturn(List.of(stations));
        return service.list();
    }

    @Test
    void asksTheCacheWithoutThisInstance() {
        listing();

        verify(cache).findPublishedElsewhere(OWN_KEY, OWN_URL);
    }

    @Test
    void linksThePublicPageOnTheInstanceTheCardCameFrom() {
        var listed = listing(published("https://feuer.example:8443/", card(STATION.toString(), "wache-nord")));

        assertEquals(1, listed.size());
        var station = listed.getFirst();
        assertEquals(STATION, station.stationUid());
        assertEquals("feuer.example", station.instanceHost());
        assertEquals("https://feuer.example:8443/public/station/wache-nord", station.publicPageUrl());
    }

    @Test
    void aStationWithoutReadableAddressIsLinkedByItsIdentifier() {
        var listed = listing(published("http://feuer.example", card(STATION.toString(), null)));

        assertEquals(
                "http://feuer.example/public/station/" + STATION,
                listed.getFirst().publicPageUrl());
    }

    @Test
    void anAddressIsWrittenAsOnePathSegment() {
        var listed = listing(published("https://feuer.example", card(STATION.toString(), "wache nord/../admin?x")));

        assertEquals(
                "https://feuer.example/public/station/wache%20nord%2F..%2Fadmin%3Fx",
                listed.getFirst().publicPageUrl());
    }

    @Test
    void aStationWhoseCardNamesNoPublicPageIsNotLinked() {
        var withoutPage = new DiscoveryStationCard(
                STATION.toString(),
                "Wache Nord",
                null,
                null,
                "DE",
                null,
                "Nordstadt",
                null,
                List.of(),
                "<10",
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                "wache-nord");

        var listed = listing(published("https://feuer.example", withoutPage));

        assertEquals(1, listed.size());
        assertNull(listed.getFirst().publicPageUrl());
    }

    @Test
    void aKeptLogoIsAddressedOnThisInstanceAndNeverOnTheOther() {
        var listed = listing(
                new PublishedRemoteStation("k", "https://feuer.example", card(STATION.toString(), null), true),
                published("https://feuer.example", card(STATION.toString(), null)));

        assertEquals(
                "/api/v1/public/discovery/remote/" + RemoteStationLogoService.fingerprint("k") + "/" + STATION
                        + "/logo?size=128",
                listed.getFirst().logoUrl());
        assertNull(listed.get(1).logoUrl());
    }

    @Test
    void cardsThatCannotBeLinkedSafelyAreLeftOut() {
        var listed = listing(
                published("javascript:alert(1)", card(STATION.toString(), null)),
                published("ftp://feuer.example", card(STATION.toString(), null)),
                published("not a url", card(STATION.toString(), null)),
                published(null, card(STATION.toString(), null)),
                published("https://feuer.example", card("not-a-uuid", null)),
                published("https://feuer.example", card(null, null)));

        assertTrue(listed.isEmpty(), listed.toString());
    }
}
