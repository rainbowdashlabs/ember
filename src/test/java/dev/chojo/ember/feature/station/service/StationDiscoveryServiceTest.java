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
import dev.chojo.ember.feature.discovery.service.RemoteStationListingService;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StationDiscoveryServiceTest {
    private static final UUID OWN = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PARTNER = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OPEN = UUID.fromString("00000000-0000-0000-0000-000000000005");

    private StationService stations;
    private FederationService federation;
    private ClusterRepository clusters;
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
        return station;
    }

    private static FederationPartner partnerOf(UUID uid) {
        var entry = mock(FederationPartner.class);
        when(entry.partnerStationId()).thenReturn(uid);
        when(entry.stationId()).thenReturn(3);
        return entry;
    }

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    @BeforeEach
    void setup() {
        stations = mock(StationService.class);
        federation = mock(FederationService.class);
        clusters = mock(ClusterRepository.class);
        service = new StationDiscoveryService(
                stations,
                mock(StationLogoService.class),
                federation,
                clusters,
                mock(RemoteStationListingService.class));
        own = station(3, OWN);
        partner = station(4, PARTNER);
        open = station(5, OPEN);
        when(open.latitude()).thenReturn(BigDecimal.ONE);
        when(open.longitude()).thenReturn(BigDecimal.TEN);
        when(open.discoveryShowKb()).thenReturn(true);
        when(open.publicKbMode()).thenReturn(PublicKbMode.ALLOW_ALL);
        var partnerEntry = partnerOf(PARTNER);
        when(federation.findPartners(3)).thenReturn(List.of(partnerEntry));
    }

    @Test
    void aSignedInStationSeesItselfFirstAndItsPartnersMarked() {
        when(stations.findDiscoverable(3)).thenReturn(List.of(partner, open));
        when(stations.findWithPublicContent(3)).thenReturn(List.of(open));
        when(stations.findById(3)).thenReturn(Optional.of(own));
        var cluster = mock(Cluster.class);
        when(cluster.name()).thenReturn("Kreis");
        when(clusters.findByStation(anyInt())).thenReturn(Optional.empty());
        when(clusters.findByStation(5)).thenReturn(Optional.of(cluster));

        var entries = service.list(true, 3);

        assertEquals(
                List.of(OWN, PARTNER, OPEN),
                entries.stream().map(e -> e.stationUid()).toList());
        assertTrue(entries.getFirst().isOwnStation());
        assertTrue(entries.get(1).alreadyFederated());
        assertTrue(entries.get(2).hasPublicKb());
        assertEquals(1.0, entries.get(2).latitude());
        assertEquals("Kreis", entries.get(2).clusterName());
        assertNull(entries.get(1).latitude());
    }

    @Test
    void aStrangerSeesOnlyWhatIsPubliclyDiscoverable() {
        when(stations.findPubliclyDiscoverable(0)).thenReturn(List.of(open));
        when(clusters.findByStation(anyInt())).thenReturn(Optional.empty());

        var entries = service.list(false, null);

        assertEquals(1, entries.size());
        assertFalse(entries.getFirst().alreadyFederated());
        verify(stations, never()).findWithPublicContent(anyInt());
    }

    @Test
    void anInviteIsOnlyForAStationOpenToIt() {
        when(stations.findPubliclyDiscoverable(0)).thenReturn(List.of(open));
        when(federation.generatePairingCode(OPEN)).thenReturn("CODE");

        assertEquals("CODE", service.inviteCode(false, OPEN));
        assertEquals(DiscoveryRefusal.INVITE_NEEDS_A_STATION, refusalOf(() -> service.inviteCode(false, null)));
        assertEquals(DiscoveryRefusal.STATION_NOT_OPEN_TO_INVITES, refusalOf(() -> service.inviteCode(true, OPEN)));
    }

    @Test
    void aFederationIsAskedForOnceAndNeverOfAPartner() {
        when(stations.findDiscoverable(3)).thenReturn(List.of(partner, open));
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
}
