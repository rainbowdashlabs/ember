/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.discovery.entity.CachedDiscoveryStation;
import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.discovery.repository.DiscoveryStationCacheRepository;
import dev.chojo.ember.feature.federation.entity.PublicPartnerSummary;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicSiteServiceTest {
    private static final UUID PARTNER = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final UUID CACHED = UUID.fromString("00000000-0000-0000-0000-000000000043");

    private StationRepository stations;
    private FederationRepository federation;
    private DiscoveryStationCacheRepository cache;
    private PublicSiteService site;

    private static Station station(boolean pagesOpen) {
        var station = mock(Station.class);
        when(station.publicPagesEnabled()).thenReturn(pagesOpen);
        return station;
    }

    @BeforeEach
    void setup() {
        stations = mock(StationRepository.class);
        federation = mock(FederationRepository.class);
        cache = mock(DiscoveryStationCacheRepository.class);
        site = new PublicSiteService(stations, federation, cache);
    }

    @Test
    void anOpenStationAnswersAndAClosedOrUnknownOneDoesNot() {
        var open = station(true);
        var closed = station(false);
        when(stations.resolveAddressedId("open")).thenReturn(Optional.of(3));
        when(stations.resolveAddressedId("closed")).thenReturn(Optional.of(4));
        when(stations.findById(3)).thenReturn(Optional.of(open));
        when(stations.findById(4)).thenReturn(Optional.of(closed));

        assertEquals(3, site.openStation("open"));
        assertEquals(
                Refusal.PUBLIC_PAGES_SWITCHED_OFF,
                assertThrows(RefusalResponse.class, () -> site.openStation("closed"))
                        .refusal());
        assertEquals(
                Refusal.STATION_NOT_HERE_BEHIND_PUBLIC_PAGE,
                assertThrows(RefusalResponse.class, () -> site.openStation("gone"))
                        .refusal());
    }

    @Test
    void aSharedPageOfAClosedStationIsAnUnknownLink() {
        var page = mock(StationPage.class);
        when(page.stationId()).thenReturn(4);
        var closed = station(false);
        when(stations.findById(4)).thenReturn(Optional.of(closed));

        assertEquals(
                Refusal.PAGE_LINK_UNKNOWN,
                assertThrows(RefusalResponse.class, () -> site.sharedPageStation(page))
                        .refusal());
    }

    @Test
    void withoutARequestEveryPartnerIsNamed() {
        var partners = List.of(new PublicPartnerSummary(PARTNER, "Nord", null, null));
        when(federation.findActivePartnerSummaries(3)).thenReturn(partners);

        assertEquals(partners, site.partners(3, " "));
        assertEquals(partners, site.partners(3, null));
        verify(cache, never()).findByStationUids(List.of());
    }

    @Test
    void requestedStationsComeFromThePartnersThenTheCacheInTheOrderAsked() {
        when(federation.findActivePartnerSummaries(3))
                .thenReturn(List.of(new PublicPartnerSummary(PARTNER, "Nord", null, null)));
        var card = new DiscoveryStationCard(
                CACHED.toString(),
                "Fern",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        when(cache.findByStationUids(List.of(CACHED.toString(), "unknown")))
                .thenReturn(List.of(new CachedDiscoveryStation("key", CACHED.toString(), card, null)));

        var named = site.partners(3, CACHED + ", " + PARTNER + ",unknown," + CACHED);

        assertEquals(
                List.of("Fern", "Nord"),
                named.stream().map(PublicPartnerSummary::name).toList());
    }
}
