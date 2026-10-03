/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.discovery.TestDiscoveryCards;
import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.discovery.service.RemoteStationListingService;
import dev.chojo.ember.feature.discovery.service.RemoteStationListingService.RemoteStation;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.OutgoingPairRequestService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.TestPublicOffers;
import dev.chojo.ember.feature.station.entity.PublicOffer;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationDiscoveryService.DiscoveryEntry;
import dev.chojo.ember.feature.station.service.StationDiscoveryService.Viewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * The public discovery list: this instance's stations as before, then the stations other instances publish,
 * each filled the same way.
 */
class StationDiscoveryRemoteStationsTest {
    private static final int OWN_ID = 1;
    private static final Station OWN = station(OWN_ID, "Eigene Wache");
    private static final Station PUBLIC = station(2, "Offene Wache");
    private static final Station PARTNER = station(3, "Partnerwache");
    private static final Station WITH_CONTENT = station(4, "Wache mit Seiten");
    private static final UUID REMOTE_UID = UUID.fromString("7f0c1f5e-3f4c-4b6f-9a51-0d1e2f3a4b5c");
    private static final UUID REMOTE_CLUSTER = UUID.fromString("0b1c2d3e-4f50-4a6b-8c7d-9e0f1a2b3c4d");
    private static final String REMOTE_INSTANCE = "https://feuer.example:8443";
    private static final String REMOTE_PAGE = REMOTE_INSTANCE + "/public/station/wache-nord";
    private static final PublicOffer NOTHING = new PublicOffer(false, false, false, false, false);

    private StationService stations;
    private FederationService federation;
    private RemoteStationListingService remote;
    private StationLogoService logos;
    private final Map<Integer, PublicOffer> offers = new HashMap<>();
    private StationDiscoveryService service;

    private static Station station(int id, String name) {
        var station = mock(Station.class);
        when(station.id()).thenReturn(id);
        when(station.uid()).thenReturn(new UUID(0, id));
        when(station.name()).thenReturn(name);
        when(station.publicKbMode()).thenReturn(PublicKbMode.OFF);
        return station;
    }

    private static DiscoveryStationCard card(String name, String clusterUid, boolean offers) {
        return new DiscoveryStationCard(
                REMOTE_UID.toString(),
                name,
                "Von drüben",
                "https://feuer.example/logo",
                "DE",
                null,
                "Nordstadt",
                REMOTE_PAGE,
                List.of(),
                "<10",
                Instant.now(),
                "Nordweg 2",
                new BigDecimal("52.5"),
                new BigDecimal("13.4"),
                clusterUid,
                clusterUid == null ? null : "Kreis Nord",
                "wache-nord",
                offers,
                offers,
                offers,
                offers,
                offers);
    }

    private static RemoteStation remoteStation(DiscoveryStationCard card, String logoUrl) {
        return new RemoteStation(REMOTE_UID, card, "feuer.example", REMOTE_INSTANCE, REMOTE_PAGE, logoUrl);
    }

    private static RemoteStation remoteStation(String name, String clusterUid) {
        return remoteStation(card(name, clusterUid, false), null);
    }

    private static FederationPartner partnerRow(UUID uid, String remoteHost) {
        return new FederationPartner(
                1,
                OWN_ID,
                uid,
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now(),
                remoteHost,
                null);
    }

    @BeforeEach
    void setup() {
        stations = mock(StationService.class);
        federation = mock(FederationService.class);
        remote = mock(RemoteStationListingService.class);
        var clusters = mock(ClusterRepository.class);
        logos = mock(StationLogoService.class);
        var publicInfo = mock(PublicStationInfoService.class);
        TestPublicOffers.stub(publicInfo, station -> offers.getOrDefault(station.id(), NOTHING));
        service = new StationDiscoveryService(
                stations, logos, federation, clusters, remote, publicInfo, mock(OutgoingPairRequestService.class));

        when(stations.findPubliclyDiscoverable(0)).thenReturn(List.of(PUBLIC));
        when(stations.findDiscoverable(OWN_ID)).thenReturn(List.of(PUBLIC, PARTNER));
        when(stations.findWithPublicContent(OWN_ID)).thenReturn(List.of(PARTNER, WITH_CONTENT));
        when(stations.findById(OWN_ID)).thenReturn(Optional.of(OWN));
        var partner = partnerRow(PARTNER.uid(), null);
        when(federation.findPartners(OWN_ID)).thenReturn(List.of(partner));
        when(remote.list()).thenReturn(List.of(remoteStation("Wache Nord", REMOTE_CLUSTER.toString())));
    }

    private static List<String> names(List<DiscoveryEntry> entries) {
        return entries.stream().map(DiscoveryEntry::name).toList();
    }

    @Test
    void aVisitorSeesThePublicLocalStationsThenTheRemoteOnes() {
        var entries = service.list(Viewer.anonymous());

        assertEquals(List.of("Offene Wache", "Wache Nord"), names(entries));
        verify(stations, never()).findWithPublicContent(anyInt());
        verify(federation, never()).findPartners(anyInt());
    }

    @Test
    void aSignedInReaderKeepsTheirOwnStationFirstAndTheirPartnersMarked() {
        var entries = service.list(new Viewer(true, OWN_ID, true));

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

        var entries = service.list(new Viewer(true, null, false));

        assertEquals(List.of("Offene Wache", "Wache mit Seiten", "Wache Nord"), names(entries));
    }

    @Test
    void aRemoteStationCarriesItsInstanceAndLinkButNothingLocal() {
        var entry = service.list(Viewer.anonymous()).getLast();

        assertEquals(REMOTE_UID, entry.stationUid());
        assertEquals("feuer.example", entry.instanceHost());
        assertEquals(REMOTE_INSTANCE, entry.instanceUrl());
        assertEquals(REMOTE_PAGE, entry.publicPageUrl());
        assertEquals("Von drüben", entry.description());
        assertEquals("Nordweg 2", entry.addressLine());
        assertEquals("wache-nord", entry.publicSlug());
        assertEquals(52.5, entry.latitude());
        assertEquals(13.4, entry.longitude());
        assertEquals(REMOTE_CLUSTER, entry.clusterUid());
        assertEquals("Kreis Nord", entry.clusterName());
        assertFalse(entry.hasLogo());
        assertNull(entry.logoUrl());
        assertFalse(entry.isOwnStation());
        assertFalse(entry.alreadyFederated());
    }

    @Test
    void aRemoteStationNamesThePublicOffersItsInstancePublishes() {
        when(remote.list()).thenReturn(List.of(remoteStation(card("Wache Nord", null, true), null)));

        var entry = service.list(Viewer.anonymous()).getLast();

        assertTrue(entry.hasPublicWiki());
        assertTrue(entry.hasPublicCalendar());
        assertTrue(entry.hasPublicBlog());
        assertTrue(entry.waitingListOpen());
        assertTrue(entry.acceptsFederation());
    }

    @Test
    void aRemoteStationFromAnOlderInstanceOffersNothing() {
        var older = TestDiscoveryCards.card(REMOTE_UID.toString())
                .name("Wache Alt")
                .country(null)
                .build();
        when(remote.list()).thenReturn(List.of(remoteStation(older, null)));

        var entry = service.list(Viewer.anonymous()).getLast();

        assertFalse(entry.hasPublicWiki());
        assertFalse(entry.hasPublicCalendar());
        assertFalse(entry.hasPublicBlog());
        assertFalse(entry.waitingListOpen());
        assertFalse(entry.acceptsFederation());
    }

    @Test
    void aLocalAndARemoteStationWithTheSameOffersAreFilledAlike() {
        var local = station(9, "Wache Nord");
        when(local.discoveryDescription()).thenReturn("Von drüben");
        when(local.discoveryShowKb()).thenReturn(true);
        when(local.acceptsFederation()).thenReturn(true);
        when(local.publicSlug()).thenReturn("wache-nord");
        when(local.addressLine()).thenReturn("Nordweg 2");
        when(local.city()).thenReturn("Nordstadt");
        when(local.country()).thenReturn("DE");
        when(local.latitude()).thenReturn(new BigDecimal("52.5"));
        when(local.longitude()).thenReturn(new BigDecimal("13.4"));
        offers.put(local.id(), new PublicOffer(true, true, true, true, true));
        when(stations.findPubliclyDiscoverable(0)).thenReturn(List.of(local));
        when(remote.list()).thenReturn(List.of(remoteStation(card("Wache Nord", null, true), null)));

        var entries = service.list(Viewer.anonymous());
        var mine = entries.getFirst();
        var theirs = entries.getLast();

        assertEquals(withoutWhereItLives(mine), withoutWhereItLives(theirs));
        assertEquals("/public/station/wache-nord", mine.publicPageUrl());
        assertEquals(REMOTE_PAGE, theirs.publicPageUrl());
    }

    private static DiscoveryEntry withoutWhereItLives(DiscoveryEntry entry) {
        return new DiscoveryEntry(
                REMOTE_UID,
                entry.name(),
                entry.description(),
                entry.hasLogo(),
                entry.logoUrl(),
                entry.hasPublicWiki(),
                entry.hasPublicCalendar(),
                entry.hasPublicBlog(),
                entry.waitingListOpen(),
                entry.acceptsFederation(),
                entry.alreadyFederated(),
                entry.isOwnStation(),
                entry.canRequest(),
                entry.canInvite(),
                entry.publicSlug(),
                null,
                entry.addressLine(),
                entry.city(),
                entry.country(),
                entry.latitude(),
                entry.longitude(),
                entry.clusterUid(),
                entry.clusterName(),
                null,
                null);
    }

    @Test
    void aRemotePartnerIsMarkedWhereTheRowNamesTheStationOnItsInstance() {
        var partner = partnerRow(REMOTE_UID, "https://FEUER.example:8443/");
        when(federation.findPartners(OWN_ID)).thenReturn(List.of(partner));

        assertTrue(service.list(new Viewer(true, OWN_ID, true)).getLast().alreadyFederated());
    }

    @Test
    void aPartnerOfTheSameIdentifierElsewhereMarksNothing() {
        var elsewhere = partnerRow(REMOTE_UID, "https://anders.example");
        var here = partnerRow(REMOTE_UID, null);
        when(federation.findPartners(OWN_ID)).thenReturn(List.of(elsewhere, here));

        assertFalse(service.list(new Viewer(true, OWN_ID, true)).getLast().alreadyFederated());
    }

    @Test
    void aRemoteStationWithAKeptLogoIsGivenTheAddressOfTheCopy() {
        String copy = "/api/v1/public/discovery/remote/ab/" + REMOTE_UID + "/logo?size=128";
        when(remote.list()).thenReturn(List.of(remoteStation(card("Wache Nord", null, false), copy)));

        var entry = service.list(Viewer.anonymous()).getLast();

        assertTrue(entry.hasLogo());
        assertEquals(copy, entry.logoUrl());
    }

    @Test
    void aLocalStationWithALogoIsGivenItsOwnAddress() {
        when(logos.exists(PUBLIC.id())).thenReturn(true);

        var entry = service.list(Viewer.anonymous()).getFirst();

        assertTrue(entry.hasLogo());
        assertEquals("/api/v1/public/stations/" + PUBLIC.uid() + "/logo?size=128", entry.logoUrl());
    }

    @Test
    void aLocalStationNamesNoInstance() {
        var entry = service.list(Viewer.anonymous()).getFirst();

        assertNull(entry.instanceHost());
        assertNull(entry.instanceUrl());
        assertNull(entry.publicPageUrl());
    }

    @Test
    void aRemoteClusterThatIsNoIdentifierGroupsNothing() {
        when(remote.list()).thenReturn(List.of(remoteStation("Wache Nord", "kein-bezeichner")));

        assertNull(service.list(Viewer.anonymous()).getLast().clusterUid());

        when(remote.list()).thenReturn(List.of(remoteStation("Wache Nord", null)));

        assertNull(service.list(Viewer.anonymous()).getLast().clusterUid());
    }
}
