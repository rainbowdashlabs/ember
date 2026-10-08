/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.auth.signing.MemoryReplayStore;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationContractRefreshService;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberPermissionResolver;
import dev.chojo.ember.feature.station.entity.MovedAway;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A partner's signed request that reaches the copy a station left on this installation when it moved
 * to another one. The copy is not where the station runs, so it answers no partner, and the refusal
 * names where the station went.
 */
class MovedStationFederationTest {
    private static final int STATION_ID = 7;
    private static final UUID LOCAL_UID = UUID.randomUUID();
    private static final UUID REMOTE_UID = UUID.randomUUID();

    private final FederationRepository federation = mock(FederationRepository.class);
    private final StationRepository stations = mock(StationRepository.class);
    private final FederationSigningService signing = mock(FederationSigningService.class);
    private final Context ctx = mock(Context.class);
    private AccessManager accessManager;

    @BeforeEach
    void signedRequestFromAnActivePartner() {
        accessManager = new AccessManager(
                mock(AccountRepository.class),
                mock(StationMemberRepository.class),
                federation,
                signing,
                new MemoryReplayStore(),
                stations,
                mock(ClusterRepository.class),
                mock(FederationContractRefreshService.class),
                mock(MemberPermissionResolver.class));
        when(ctx.header("X-Federation-Station-Id")).thenReturn(REMOTE_UID.toString());
        when(ctx.header("X-Federation-Signature")).thenReturn("signature");
        when(ctx.header("X-Federation-Timestamp")).thenReturn(Instant.now().toString());
        when(ctx.header("X-Federation-Nonce"))
                .thenAnswer(invocation -> UUID.randomUUID().toString());
        when(ctx.header("X-Federation-Target-Station-Id")).thenReturn(LOCAL_UID.toString());
        when(ctx.method()).thenReturn(HandlerType.GET);
        when(ctx.path()).thenReturn("/api/v1/remote/ping");
        when(ctx.body()).thenReturn("");
        var partner = new FederationPartner(
                1,
                STATION_ID,
                REMOTE_UID,
                null,
                "own-key",
                "partner-key",
                FederationPartner.FederationStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now(),
                "https://partner.example",
                "Partner");
        when(federation.findPartnerByLocalAndRemoteStationUid(LOCAL_UID, REMOTE_UID))
                .thenReturn(Optional.of(partner));
        when(stations.resolveUid(STATION_ID)).thenReturn(LOCAL_UID);
        when(signing.verify(anyString(), anyString(), any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(true);
    }

    @Test
    void aStationRunningHereAnswersItsPartner() {
        when(stations.movedAway(STATION_ID)).thenReturn(Optional.empty());

        assertTrue(accessManager.resolveFederationSession(ctx).isPresent());
    }

    @Test
    void theCopyOfAMovedStationRefusesWithItsNewAddress() {
        when(stations.movedAway(STATION_ID))
                .thenReturn(Optional.of(new MovedAway(Instant.now(), "https://new-home.example")));

        var refusal = assertThrows(RefusalResponse.class, () -> accessManager.resolveFederationSession(ctx));

        assertEquals(StationRefusal.STATION_MOVED_AWAY, refusal.refusal());
        assertEquals(RefusalDetail.text("https://new-home.example"), refusal.detail());
    }
}
