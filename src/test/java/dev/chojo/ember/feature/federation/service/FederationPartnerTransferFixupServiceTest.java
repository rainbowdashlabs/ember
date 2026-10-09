/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.lifecycle.TaskScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.feature.federation.FederationTestContracts.pathIs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The DB-touching methods on {@code FederationPartnerTransferFixupService} (rewriteAfterImport,
 * flipSourceSideRetainedPartners) are exercised by the integration-style transfer tests; this
 * unit test focuses on {@code announceNewHostToRemotePartners}, which is pure orchestration
 * over the repository and the HTTP client.
 */
class FederationPartnerTransferFixupServiceTest {

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

    private static FederationHttpClient signingClient() {
        var http = mock(FederationHttpClient.class);
        when(http.canSign(1)).thenReturn(true);
        return http;
    }

    private final TaskScheduler scheduler = mock(TaskScheduler.class);

    private FederationPartnerTransferFixupService newService(
            FederationRepository federationRepository, FederationHttpClient httpClient) {
        return new FederationPartnerTransferFixupService(
                federationRepository, httpClient, mock(StationKeyStore.class), scheduler);
    }

    /** Runs the retry the service asked the scheduler for after the given wait. */
    private void runRetryAfter(Duration wait) {
        var retry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).later(anyString(), eq(wait), retry.capture());
        retry.getValue().run();
    }

    /**
     * A partner that could not be told where the station runs now is asked again later, and only that
     * partner: the ones that heard are not bothered twice.
     */
    @Test
    void announceAsksAgainOnlyThePartnersThatDidNotHear() {
        var repo = mock(FederationRepository.class);
        var http = signingClient();
        var unreachable = remote(10, "https://partner-a.example");
        var reached = remote(11, "https://partner-b.example");
        when(repo.findPartners(1)).thenReturn(List.of(unreachable, reached));
        when(http.post(eq("https://partner-a.example"), pathIs("/remote/announce"), any(), any(UUID.class), eq(1)))
                .thenReturn(false, true);
        when(http.post(eq("https://partner-b.example"), pathIs("/remote/announce"), any(), any(UUID.class), eq(1)))
                .thenReturn(true);

        newService(repo, http).announceNewHostToRemotePartners(1, "https://new.example.org");
        runRetryAfter(FederationPartnerTransferFixupService.ANNOUNCE_RETRIES.getFirst());

        verify(http, times(2))
                .post(eq("https://partner-a.example"), pathIs("/remote/announce"), any(), any(UUID.class), eq(1));
        verify(http).post(eq("https://partner-b.example"), pathIs("/remote/announce"), any(), any(UUID.class), eq(1));
        verify(scheduler, times(1)).later(anyString(), any(Duration.class), any(Runnable.class));
    }

    @Test
    void announceAsksNobodyAgainOnceEveryPartnerHeard() {
        var repo = mock(FederationRepository.class);
        var http = signingClient();
        when(repo.findPartners(1)).thenReturn(List.of(remote(10, "https://partner-a.example")));
        when(http.post(anyString(), pathIs("/remote/announce"), any(), any(UUID.class), eq(1)))
                .thenReturn(true);

        newService(repo, http).announceNewHostToRemotePartners(1, "https://new.example.org");

        verify(scheduler, never()).later(anyString(), any(Duration.class), any(Runnable.class));
    }

    /** A partnership that ended before the retry came round is not asked again. */
    @Test
    void announceDoesNotAskAgainWhereThePartnershipEnded() {
        var repo = mock(FederationRepository.class);
        var http = signingClient();
        when(repo.findPartners(1))
                .thenReturn(List.of(remote(10, "https://partner-a.example")))
                .thenReturn(List.of());
        when(http.post(anyString(), pathIs("/remote/announce"), any(), any(UUID.class), eq(1)))
                .thenReturn(false);

        newService(repo, http).announceNewHostToRemotePartners(1, "https://new.example.org");
        runRetryAfter(FederationPartnerTransferFixupService.ANNOUNCE_RETRIES.getFirst());

        verify(http).post(anyString(), pathIs("/remote/announce"), any(), any(UUID.class), eq(1));
    }

    /** After the last wait the service stops asking, and the copy at the old address answers for it. */
    @Test
    void announceGivesUpAfterTheLastRetry() {
        var repo = mock(FederationRepository.class);
        var http = signingClient();
        when(repo.findPartners(1)).thenReturn(List.of(remote(10, "https://partner-a.example")));
        when(http.post(anyString(), pathIs("/remote/announce"), any(), any(UUID.class), eq(1)))
                .thenReturn(false);
        var retries = new ArrayList<Runnable>();
        doAnswer(invocation -> retries.add(invocation.getArgument(2)))
                .when(scheduler)
                .later(anyString(), any(Duration.class), any(Runnable.class));

        newService(repo, http).announceNewHostToRemotePartners(1, "https://new.example.org");
        for (int i = 0; i < retries.size(); i++) retries.get(i).run();

        assertEquals(FederationPartnerTransferFixupService.ANNOUNCE_RETRIES.size(), retries.size());
        verify(http, times(retries.size() + 1))
                .post(anyString(), pathIs("/remote/announce"), any(), any(UUID.class), eq(1));
    }

    @Test
    void announceSkipsWhenInstanceUrlMissing() {
        var repo = mock(FederationRepository.class);
        var http = signingClient();
        var svc = newService(repo, http);

        svc.announceNewHostToRemotePartners(1, null);
        svc.announceNewHostToRemotePartners(1, "   ");

        verify(http, never()).canSign(anyInt());
        verify(repo, never()).findPartners(anyInt());
        verify(http, never()).post(anyString(), any(FederationRequest.class), any(), any(UUID.class), anyInt());
    }

    @Test
    void announceSkipsWhenStationCannotSign() {
        var repo = mock(FederationRepository.class);
        var http = mock(FederationHttpClient.class);
        var svc = newService(repo, http);
        when(repo.findPartners(1)).thenReturn(List.of(remote(10, "https://partner-a.example")));

        svc.announceNewHostToRemotePartners(1, "https://new.example.org");

        verify(http).canSign(1);
        verify(http, never()).post(anyString(), any(FederationRequest.class), any(), any(UUID.class), anyInt());
    }

    @Test
    void announceWithoutRemotePartnersNeverAsksForAKey() {
        var repo = mock(FederationRepository.class);
        var http = mock(FederationHttpClient.class);
        var svc = newService(repo, http);
        when(repo.findPartners(1)).thenReturn(List.of(local(12)));

        svc.announceNewHostToRemotePartners(1, "https://new.example.org");

        verify(http, never()).canSign(anyInt());
    }

    @Test
    void announcePostsToActiveRemotePartnersOnly() {
        var repo = mock(FederationRepository.class);
        var http = signingClient();
        var remoteA = remote(10, "https://partner-a.example");
        var remoteB = remote(11, "https://partner-b.example");
        when(repo.findPartners(1)).thenReturn(List.of(remoteA, remoteB, local(12), suspended(13, "https://x.example")));
        when(http.post(anyString(), pathIs("/remote/announce"), any(), any(UUID.class), eq(1)))
                .thenReturn(true);

        var svc = newService(repo, http);
        svc.announceNewHostToRemotePartners(1, "https://new.example.org");

        verify(http)
                .post(
                        eq("https://partner-a.example"),
                        pathIs("/remote/announce"),
                        any(),
                        eq(remoteA.partnerStationId()),
                        eq(1));
        verify(http)
                .post(
                        eq("https://partner-b.example"),
                        pathIs("/remote/announce"),
                        any(),
                        eq(remoteB.partnerStationId()),
                        eq(1));
        verify(http, times(2)).post(anyString(), pathIs("/remote/announce"), any(), any(UUID.class), eq(1));
    }

    @Test
    void announceContinuesAfterPostThrows() {
        var repo = mock(FederationRepository.class);
        var http = signingClient();
        var first = remote(10, "https://partner-a.example");
        var second = remote(11, "https://partner-b.example");
        when(repo.findPartners(1)).thenReturn(List.of(first, second));
        when(http.post(eq("https://partner-a.example"), pathIs("/remote/announce"), any(), any(UUID.class), anyInt()))
                .thenThrow(new RuntimeException("network down"));
        when(http.post(eq("https://partner-b.example"), pathIs("/remote/announce"), any(), any(UUID.class), anyInt()))
                .thenReturn(true);

        var svc = newService(repo, http);
        svc.announceNewHostToRemotePartners(1, "https://new.example.org");

        verify(http, times(2)).post(anyString(), pathIs("/remote/announce"), any(), any(UUID.class), anyInt());
    }

    @Test
    void announceCountsPostReturningFalseAsFailure() {
        var repo = mock(FederationRepository.class);
        var http = signingClient();
        var only = remote(10, "https://partner-a.example");
        when(repo.findPartners(1)).thenReturn(List.of(only));
        when(http.post(anyString(), any(FederationRequest.class), any(), any(UUID.class), anyInt()))
                .thenReturn(false);

        var svc = newService(repo, http);
        svc.announceNewHostToRemotePartners(1, "https://new.example.org");

        verify(http).post(anyString(), any(FederationRequest.class), any(), any(UUID.class), anyInt());
    }
}
