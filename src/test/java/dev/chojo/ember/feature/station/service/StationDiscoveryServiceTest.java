/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.refusal.DiscoveryRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.discovery.TestDiscoveryCards;
import dev.chojo.ember.feature.discovery.entity.PublishedRemoteStation;
import dev.chojo.ember.feature.discovery.service.RemoteStationListingService;
import dev.chojo.ember.feature.discovery.service.RemoteStationListingService.RemoteStation;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.PairRequest;
import dev.chojo.ember.feature.federation.entity.PairRequestDirection;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.OutgoingPairRequestService;
import dev.chojo.ember.feature.federation.service.OutgoingPairRequestService.RemoteTarget;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.TestPublicOffers;
import dev.chojo.ember.feature.station.entity.PublicOffer;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationDiscoveryService.DiscoveryEntry;
import dev.chojo.ember.feature.station.service.StationDiscoveryService.Viewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StationDiscoveryServiceTest {
    private static final UUID OWN = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PARTNER = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OPEN = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID REMOTE = UUID.fromString("7f0c1f5e-3f4c-4b6f-9a51-0d1e2f3a4b5c");
    private static final String REMOTE_URL = "https://feuer.example:8443";
    private static final Viewer MANAGER = new Viewer(true, 3, true);

    private StationService stations;
    private FederationService federation;
    private ClusterRepository clusters;
    private RemoteStationListingService remote;
    private OutgoingPairRequestService outgoing;
    private StationDiscoveryService service;
    private Station own;
    private Station partner;
    private Station open;

    private static Station station(int id, UUID uid) {
        var station = mock(Station.class);
        when(station.id()).thenReturn(id);
        when(station.uid()).thenReturn(uid);
        when(station.name()).thenReturn("Wache " + id);
        when(station.publicKbMode()).thenReturn(PublicKbMode.OFF);
        when(station.acceptsFederation()).thenReturn(true);
        return station;
    }

    private static FederationPartner partnerOf(UUID uid) {
        return new FederationPartner(
                1,
                3,
                uid,
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now(),
                null,
                null);
    }

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    @BeforeEach
    void setup() {
        stations = mock(StationService.class);
        federation = mock(FederationService.class);
        clusters = mock(ClusterRepository.class);
        var publicInfo = mock(PublicStationInfoService.class);
        remote = mock(RemoteStationListingService.class);
        outgoing = mock(OutgoingPairRequestService.class);
        service = new StationDiscoveryService(
                stations, mock(StationLogoService.class), federation, clusters, remote, publicInfo, outgoing);
        own = station(3, OWN);
        partner = station(4, PARTNER);
        open = station(5, OPEN);
        when(open.latitude()).thenReturn(BigDecimal.ONE);
        when(open.longitude()).thenReturn(BigDecimal.TEN);
        when(open.discoveryShowKb()).thenReturn(true);
        when(open.publicKbMode()).thenReturn(PublicKbMode.ALLOW_ALL);
        TestPublicOffers.stub(
                publicInfo,
                station -> station.id() == 5
                        ? new PublicOffer(true, false, false, false, false)
                        : new PublicOffer(false, false, false, false, false));
        when(federation.findPartners(3)).thenReturn(List.of(partnerOf(PARTNER)));
    }

    private List<DiscoveryEntry> localListing(Viewer viewer) {
        when(stations.findDiscoverable(3)).thenReturn(List.of(partner, open));
        when(stations.findWithPublicContent(3)).thenReturn(List.of(open));
        when(stations.findById(3)).thenReturn(Optional.of(own));
        return service.list(viewer);
    }

    @Test
    void aSignedInStationSeesItselfFirstAndItsPartnersMarked() {
        var cluster = mock(Cluster.class);
        when(cluster.name()).thenReturn("Kreis");
        when(clusters.findByStations(List.of(3, 4, 5))).thenReturn(Map.of(5, cluster));

        var entries = localListing(MANAGER);

        assertEquals(
                List.of(OWN, PARTNER, OPEN),
                entries.stream().map(DiscoveryEntry::stationUid).toList());
        assertTrue(entries.getFirst().isOwnStation());
        assertFalse(entries.get(1).isOwnStation());
        assertTrue(entries.get(1).alreadyFederated());
        assertTrue(entries.get(2).hasPublicWiki());
        assertEquals("/public/station/" + OPEN, entries.get(2).publicPageUrl());
        assertNull(entries.get(1).publicPageUrl());
        assertEquals(1.0, entries.get(2).latitude());
        assertEquals("Kreis", entries.get(2).clusterName());
        assertNull(entries.get(1).clusterName());
        assertNull(entries.get(1).latitude());
    }

    @Test
    void eachCardSaysWhetherTheManagerMayAskOrInvite() {
        var entries = localListing(MANAGER);

        assertFalse(entries.getFirst().canRequest(), "never the own station");
        assertFalse(entries.getFirst().canInvite());
        assertFalse(entries.get(1).canRequest(), "never a partner");
        assertFalse(entries.get(1).canInvite());
        assertTrue(entries.get(2).canRequest());
        assertTrue(entries.get(2).canInvite());
    }

    @Test
    void aMemberWithoutTheFederationPermissionMayInviteButNotAsk() {
        var entries = localListing(new Viewer(true, 3, false));

        assertFalse(entries.get(2).canRequest());
        assertTrue(entries.get(2).canInvite());
        verify(outgoing, never()).waiting(anyInt());
    }

    @Test
    void aStationThatTakesNoRequestsOffersNeither() {
        when(open.acceptsFederation()).thenReturn(false);

        var entries = localListing(MANAGER);

        assertFalse(entries.get(2).canRequest());
        assertFalse(entries.get(2).canInvite());
    }

    @Test
    void aStrangerSeesOnlyWhatIsPubliclyDiscoverable() {
        when(stations.findPubliclyDiscoverable(0)).thenReturn(List.of(open));

        var entries = service.list(Viewer.anonymous());

        assertEquals(1, entries.size());
        assertFalse(entries.getFirst().alreadyFederated());
        assertFalse(entries.getFirst().isOwnStation());
        assertFalse(entries.getFirst().canRequest());
        assertTrue(entries.getFirst().canInvite());
        verify(stations, never()).findWithPublicContent(anyInt());
        verify(federation, never()).findPartners(anyInt());
    }

    @Test
    void aStationOfAnotherInstanceAlreadyAskedIsNotOfferedToBeAskedAgain() {
        var asked = TestDiscoveryCards.card(REMOTE.toString())
                .acceptsFederation(true)
                .build();
        var notAsked = TestDiscoveryCards.card(UUID.randomUUID().toString())
                .acceptsFederation(true)
                .build();
        when(remote.list())
                .thenReturn(List.of(
                        new RemoteStation(REMOTE, asked, "feuer.example", REMOTE_URL, null, null),
                        new RemoteStation(
                                UUID.fromString(notAsked.stationUid()),
                                notAsked,
                                "feuer.example",
                                REMOTE_URL,
                                null,
                                null)));
        when(outgoing.waiting(3)).thenReturn(List.of(waitingFor(REMOTE, REMOTE_URL + "/")));

        var entries = localListing(MANAGER);
        var askedEntry = entries.get(entries.size() - 2);
        var notAskedEntry = entries.getLast();

        assertEquals(REMOTE, askedEntry.stationUid());
        assertFalse(askedEntry.canRequest(), "a request to it already waits");
        assertTrue(askedEntry.canInvite());
        assertFalse(askedEntry.isOwnStation());
        assertTrue(notAskedEntry.canRequest());
    }

    private static PairRequest waitingFor(UUID stationUid, String baseUrl) {
        return new PairRequest(
                1,
                3,
                PairRequestDirection.OUTGOING,
                stationUid,
                "Wache Fern",
                baseUrl,
                "instance-key",
                null,
                null,
                PairRequestStatus.PENDING,
                Instant.now(),
                null,
                null);
    }

    @Test
    void anInviteIsOnlyForAStationOpenToIt() {
        when(stations.findDiscoverable(OPEN, false)).thenReturn(Optional.of(open));
        when(federation.generatePairingCode(OPEN)).thenReturn("CODE");

        assertEquals("CODE", service.inviteCode(false, OPEN));
        assertEquals(DiscoveryRefusal.INVITE_NEEDS_A_STATION, refusalOf(() -> service.inviteCode(false, null)));
        assertEquals(DiscoveryRefusal.STATION_NOT_OPEN_TO_INVITES, refusalOf(() -> service.inviteCode(true, OPEN)));
    }

    @Test
    void aFederationIsAskedForOnceAndNeverOfAPartner() {
        when(stations.findDiscoverable(OPEN, true)).thenReturn(Optional.of(open));
        when(stations.findDiscoverable(PARTNER, true)).thenReturn(Optional.of(partner));
        var pending = partnerOf(OPEN);

        service.requestFederation(3, OPEN);
        verify(federation).createPairRequest(3, 5);

        when(federation.findPendingRequests(5)).thenReturn(List.of(pending));
        assertEquals(
                DiscoveryRefusal.FEDERATION_REQUEST_ALREADY_SENT, refusalOf(() -> service.requestFederation(3, OPEN)));
        assertEquals(DiscoveryRefusal.ALREADY_FEDERATED, refusalOf(() -> service.requestFederation(3, PARTNER)));
        assertEquals(
                DiscoveryRefusal.STATION_NOT_OPEN_TO_FEDERATION,
                refusalOf(() -> service.requestFederation(3, UUID.randomUUID())));
        assertEquals(
                DiscoveryRefusal.FEDERATION_REQUEST_NEEDS_A_STATION,
                refusalOf(() -> service.requestFederation(3, null)));
    }

    @Test
    void aStationDoesNotAskItself() {
        when(stations.findDiscoverable(OWN, true)).thenReturn(Optional.of(own));

        assertEquals(
                DiscoveryRefusal.STATION_NOT_OPEN_TO_FEDERATION, refusalOf(() -> service.requestFederation(3, OWN)));
        verify(federation, never()).createPairRequest(anyInt(), anyInt());
    }

    @Test
    void aStationOfAnotherInstanceIsAskedThroughItsInstance() {
        when(remote.findPublished(REMOTE)).thenReturn(Optional.of(published()));

        service.requestFederation(3, REMOTE);

        verify(outgoing).send(3, new RemoteTarget(REMOTE, REMOTE_URL, "instance-key"));
        verify(federation, never()).createPairRequest(anyInt(), anyInt());
    }

    @Test
    void aStationOfAnotherInstanceIsInvitedWithACodeNamingItsInstance() {
        when(remote.findPublished(REMOTE)).thenReturn(Optional.of(published()));
        when(federation.generateRemotePairingCode(REMOTE, REMOTE_URL)).thenReturn("REMOTE-CODE");

        assertEquals("REMOTE-CODE", service.inviteCode(false, REMOTE));
        verify(stations).findDiscoverable(REMOTE, false);
        verify(remote, never()).list();
        verify(stations, never()).findPubliclyDiscoverable(any(Integer.class));
    }

    private static PublishedRemoteStation published() {
        var card = TestDiscoveryCards.card(REMOTE.toString()).name("Wache Fern").build();
        return new PublishedRemoteStation("instance-key", REMOTE_URL, card, false);
    }
}
