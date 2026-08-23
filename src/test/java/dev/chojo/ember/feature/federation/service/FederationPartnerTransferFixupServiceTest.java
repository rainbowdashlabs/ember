/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.feature.federation.FederationTestContracts.pathIs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The DB-touching methods on {@code FederationPartnerTransferFixupService} (rewriteAfterImport,
 * flipSourceSideRetainedPartners) are exercised by the integration-style transfer tests; this
 * unit test focuses on {@code announceNewHostToRemotePartners}, which is pure orchestration
 * over the repository / HTTP client / station lookups.
 */
class FederationPartnerTransferFixupServiceTest {

    private static final String PRIVATE_KEY = "fake-private-key-base64";

    private static FederationPartner remote(int id, String host) {
        return new FederationPartner(
                id,
                1,
                UUID.randomUUID(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now(),
                host,
                "PartnerName");
    }

    private static FederationPartner local(int id) {
        return new FederationPartner(
                id,
                1,
                UUID.randomUUID(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now(),
                null,
                "LocalPartner");
    }

    private static FederationPartner suspended(int id, String host) {
        return new FederationPartner(
                id,
                1,
                UUID.randomUUID(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.SUSPENDED,
                null,
                Instant.now(),
                Instant.now(),
                host,
                "SuspendedPartner");
    }

    private static Station station(int id, String privateKey) {
        return new Station(
                id,
                UUID.randomUUID(),
                "Moved Station",
                "Europe/Berlin",
                "de-DE",
                null,
                "ember",
                true,
                null,
                ThemeFeel.ROUNDED,
                true,
                PublicKbMode.OFF,
                privateKey,
                DiscoveryVisibility.NONE,
                null,
                false,
                false,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                StationKind.REGULAR,
                null,
                false);
    }

    private FederationPartnerTransferFixupService newService(
            FederationRepository federationRepository,
            FederationHttpClient httpClient,
            StationRepository stationRepository) {
        return new FederationPartnerTransferFixupService(federationRepository, httpClient, stationRepository);
    }

    @Test
    void announceSkipsWhenInstanceUrlMissing() {
        var repo = mock(FederationRepository.class);
        var http = mock(FederationHttpClient.class);
        var stations = mock(StationRepository.class);
        var svc = newService(repo, http, stations);

        svc.announceNewHostToRemotePartners(1, null);
        svc.announceNewHostToRemotePartners(1, "   ");

        verify(stations, never()).findById(anyInt());
        verify(repo, never()).findPartners(anyInt());
        verify(http, never())
                .post(anyString(), any(FederationRequest.class), any(), any(UUID.class), anyInt(), anyString());
    }

    @Test
    void announceSkipsWhenStationMissing() {
        var repo = mock(FederationRepository.class);
        var http = mock(FederationHttpClient.class);
        var stations = mock(StationRepository.class);
        when(stations.findById(1)).thenReturn(Optional.empty());
        var svc = newService(repo, http, stations);

        svc.announceNewHostToRemotePartners(1, "https://new.example.org");

        verify(stations).findById(1);
        verify(repo, never()).findPartners(anyInt());
        verify(http, never())
                .post(anyString(), any(FederationRequest.class), any(), any(UUID.class), anyInt(), anyString());
    }

    @Test
    void announceSkipsWhenStationHasNoPrivateKey() {
        var repo = mock(FederationRepository.class);
        var http = mock(FederationHttpClient.class);
        var stations = mock(StationRepository.class);
        when(stations.findById(1)).thenReturn(Optional.of(station(1, null)));
        var svc = newService(repo, http, stations);

        svc.announceNewHostToRemotePartners(1, "https://new.example.org");

        verify(repo, never()).findPartners(anyInt());
        verify(http, never())
                .post(anyString(), any(FederationRequest.class), any(), any(UUID.class), anyInt(), anyString());
    }

    @Test
    void announcePostsToActiveRemotePartnersOnly() {
        var repo = mock(FederationRepository.class);
        var http = mock(FederationHttpClient.class);
        var stations = mock(StationRepository.class);
        when(stations.findById(1)).thenReturn(Optional.of(station(1, PRIVATE_KEY)));
        var remoteA = remote(10, "https://partner-a.example");
        var remoteB = remote(11, "https://partner-b.example");
        when(repo.findPartners(1)).thenReturn(List.of(remoteA, remoteB, local(12), suspended(13, "https://x.example")));
        when(http.post(anyString(), pathIs("/remote/announce"), any(), any(UUID.class), eq(1), eq(PRIVATE_KEY)))
                .thenReturn(true);

        var svc = newService(repo, http, stations);
        svc.announceNewHostToRemotePartners(1, "https://new.example.org");

        verify(http)
                .post(
                        eq("https://partner-a.example"),
                        pathIs("/remote/announce"),
                        any(),
                        eq(remoteA.partnerStationId()),
                        eq(1),
                        eq(PRIVATE_KEY));
        verify(http)
                .post(
                        eq("https://partner-b.example"),
                        pathIs("/remote/announce"),
                        any(),
                        eq(remoteB.partnerStationId()),
                        eq(1),
                        eq(PRIVATE_KEY));
        verify(http, times(2))
                .post(anyString(), pathIs("/remote/announce"), any(), any(UUID.class), eq(1), eq(PRIVATE_KEY));
    }

    @Test
    void announceContinuesAfterPostThrows() {
        var repo = mock(FederationRepository.class);
        var http = mock(FederationHttpClient.class);
        var stations = mock(StationRepository.class);
        when(stations.findById(1)).thenReturn(Optional.of(station(1, PRIVATE_KEY)));
        var first = remote(10, "https://partner-a.example");
        var second = remote(11, "https://partner-b.example");
        when(repo.findPartners(1)).thenReturn(List.of(first, second));
        when(http.post(
                        eq("https://partner-a.example"),
                        pathIs("/remote/announce"),
                        any(),
                        any(UUID.class),
                        anyInt(),
                        anyString()))
                .thenThrow(new RuntimeException("network down"));
        when(http.post(
                        eq("https://partner-b.example"),
                        pathIs("/remote/announce"),
                        any(),
                        any(UUID.class),
                        anyInt(),
                        anyString()))
                .thenReturn(true);

        var svc = newService(repo, http, stations);
        svc.announceNewHostToRemotePartners(1, "https://new.example.org");

        verify(http, times(2))
                .post(anyString(), pathIs("/remote/announce"), any(), any(UUID.class), anyInt(), anyString());
    }

    @Test
    void announceCountsPostReturningFalseAsFailure() {
        var repo = mock(FederationRepository.class);
        var http = mock(FederationHttpClient.class);
        var stations = mock(StationRepository.class);
        when(stations.findById(1)).thenReturn(Optional.of(station(1, PRIVATE_KEY)));
        var only = remote(10, "https://partner-a.example");
        when(repo.findPartners(1)).thenReturn(List.of(only));
        when(http.post(anyString(), any(FederationRequest.class), any(), any(UUID.class), anyInt(), anyString()))
                .thenReturn(false);

        var svc = newService(repo, http, stations);
        svc.announceNewHostToRemotePartners(1, "https://new.example.org");

        verify(http).post(anyString(), any(FederationRequest.class), any(), any(UUID.class), anyInt(), anyString());
    }
}
