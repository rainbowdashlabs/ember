/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.discovery.service.RemoteStationListingService;
import dev.chojo.ember.feature.discovery.service.RemoteStationListingService.RemoteStation;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationDiscoveryService.DiscoveryEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The public discovery list: this instance's stations as before, then the stations other instances publish.
 */
class StationDiscoveryRemoteStationsTest {
    private static final int OWN_ID = 1;
    private static final Station OWN = station(OWN_ID, "Eigene Wache");
    private static final Station PUBLIC = station(2, "Offene Wache");
    private static final Station PARTNER = station(3, "Partnerwache");
    private static final Station WITH_CONTENT = station(4, "Wache mit Seiten");
    private static final UUID REMOTE_UID = UUID.fromString("7f0c1f5e-3f4c-4b6f-9a51-0d1e2f3a4b5c");
    private static final UUID REMOTE_CLUSTER = UUID.fromString("0b1c2d3e-4f50-4a6b-8c7d-9e0f1a2b3c4d");

    private StationService stations;
    private FederationService federation;
    private RemoteStationListingService remote;
    private StationDiscoveryService service;

    private static Station station(int id, String name) {
        var station = mock(Station.class);
        when(station.id()).thenReturn(id);
        when(station.uid()).thenReturn(new UUID(0, id));
        when(station.name()).thenReturn(name);
        when(station.publicKbMode()).thenReturn(PublicKbMode.OFF);
        return station;
    }

    private static RemoteStation remoteStation(String name, String clusterUid) {
        var card = new DiscoveryStationCard(
                REMOTE_UID.toString(),
                name,
                "Von drüben",
                "https://feuer.example/logo",
                "DE",
                null,
                "Nordstadt",
                null,
                List.of(),
                "<10",
                Instant.now(),
                null,
                new BigDecimal("52.5"),
                new BigDecimal("13.4"),
                clusterUid,
                "Kreis Nord",
                "wache-nord");
        return new RemoteStation(REMOTE_UID, card, "feuer.example", "https://feuer.example/public/station/wache-nord");
    }

    @BeforeEach
    void setup() {
        stations = mock(StationService.class);
        federation = mock(FederationService.class);
        remote = mock(RemoteStationListingService.class);
        var clusters = mock(ClusterRepository.class);
        when(clusters.findByStation(anyInt())).thenReturn(Optional.empty());
        service = new StationDiscoveryService(stations, mock(StationLogoService.class), federation, clusters, remote);

        when(stations.findPubliclyDiscoverable(0)).thenReturn(List.of(PUBLIC));
        when(stations.findDiscoverable(OWN_ID)).thenReturn(List.of(PUBLIC, PARTNER));
        when(stations.findWithPublicContent(OWN_ID)).thenReturn(List.of(PARTNER, WITH_CONTENT));
        when(stations.findById(OWN_ID)).thenReturn(Optional.of(OWN));
        var partnerUid = PARTNER.uid();
        var partner = mock(FederationPartner.class);
        when(partner.partnerStationId()).thenReturn(partnerUid);
        when(federation.findPartners(OWN_ID)).thenReturn(List.of(partner));
        when(remote.list()).thenReturn(List.of(remoteStation("Wache Nord", REMOTE_CLUSTER.toString())));
    }

    private static List<String> names(List<DiscoveryEntry> entries) {
        return entries.stream().map(DiscoveryEntry::name).toList();
    }

    @Test
    void aVisitorSeesThePublicLocalStationsThenTheRemoteOnes() {
        var entries = service.list(false, null);

        assertEquals(List.of("Offene Wache", "Wache Nord"), names(entries));
        verify(stations, never()).findWithPublicContent(anyInt());
        verify(federation, never()).findPartners(anyInt());
    }

    @Test
    void aSignedInReaderKeepsTheirOwnStationFirstAndTheirPartnersMarked() {
        var entries = service.list(true, OWN_ID);

        assertEquals(
                List.of("Eigene Wache", "Offene Wache", "Partnerwache", "Wache mit Seiten", "Wache Nord"),
                names(entries));
        assertTrue(entries.getFirst().isOwnStation());
        assertTrue(entries.get(2).alreadyFederated());
        assertFalse(entries.get(1).alreadyFederated());
    }

    @Test
    void aSignedInReaderWithoutStationSeesEveryVisibleLocalStation() {
        when(stations.findDiscoverable(0)).thenReturn(List.of(PUBLIC));
        when(stations.findWithPublicContent(0)).thenReturn(List.of(WITH_CONTENT));

        var entries = service.list(true, null);

        assertEquals(List.of("Offene Wache", "Wache mit Seiten", "Wache Nord"), names(entries));
    }

    @Test
    void aRemoteStationCarriesItsInstanceAndLinkButNothingLocal() {
        var entry = service.list(false, null).getLast();

        assertEquals(REMOTE_UID, entry.stationUid());
        assertEquals("feuer.example", entry.instanceHost());
        assertEquals("https://feuer.example/public/station/wache-nord", entry.publicPageUrl());
        assertEquals("Von drüben", entry.description());
        assertEquals("wache-nord", entry.publicSlug());
        assertEquals(52.5, entry.latitude());
        assertEquals(13.4, entry.longitude());
        assertEquals(REMOTE_CLUSTER, entry.clusterUid());
        assertEquals("Kreis Nord", entry.clusterName());
        assertFalse(entry.hasLogo());
        assertFalse(entry.isOwnStation());
        assertFalse(entry.alreadyFederated());
    }

    @Test
    void aLocalStationNamesNoInstance() {
        var entry = service.list(false, null).getFirst();

        assertNull(entry.instanceHost());
        assertNull(entry.publicPageUrl());
    }

    @Test
    void aRemoteClusterThatIsNoIdentifierGroupsNothing() {
        when(remote.list()).thenReturn(List.of(remoteStation("Wache Nord", "kein-bezeichner")));

        assertNull(service.list(false, null).getLast().clusterUid());

        when(remote.list()).thenReturn(List.of(remoteStation("Wache Nord", null)));

        assertNull(service.list(false, null).getLast().clusterUid());
    }
}
