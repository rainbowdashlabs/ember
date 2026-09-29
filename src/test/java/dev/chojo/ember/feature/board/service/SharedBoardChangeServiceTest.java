/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.route.RemoteBoardWebhookRoutes;
import dev.chojo.ember.feature.board.service.FederatedBoardNotificationService.BoardRenamedPayload;
import dev.chojo.ember.feature.board.service.FederatedBoardNotificationService.BoardUnsharedPayload;
import dev.chojo.ember.feature.board.service.FederatedBoardNotificationService.ShareModeChangedPayload;
import dev.chojo.ember.feature.board.service.FederatedBoardService.PartnerShareConfig;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationWebhookService;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Each change a partner must hear about reaches every remote partner holding the board exactly once,
 * and never goes over the wire to a partner on this instance.
 */
class SharedBoardChangeServiceTest extends RepositoryTestBase {
    private static final String REMOTE_HOST = "https://partner.example";
    private static final long SETTLE_MILLIS = 500;

    private static FederationHttpClient httpClient;
    private static FederatedBoardService federatedBoards;
    private static SharedBoardChangeService changes;
    private static Station station;
    private static Station localPartnerStation;
    private static Board board;
    private static int remotePartnerId;
    private static int localPartnerId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("SharedBoardChanges");
        localPartnerStation = stationRepo.create("SharedBoardChangesLocal");
        board = boardRepo.create(station.id(), "Shared Board", "Desc", "SHB");
        remotePartnerId = partner(UUID.randomUUID(), REMOTE_HOST);
        localPartnerId = partner(localPartnerStation.uid(), null);

        httpClient = mock(FederationHttpClient.class);
        var keyedStation = mock(Station.class);
        when(keyedStation.federationPrivateKey()).thenReturn("private-key");
        var stations = mock(StationRepository.class);
        when(stations.findById(station.id())).thenReturn(Optional.of(keyedStation));
        var webhooks = new FederationWebhookService(new FederationRepository(), httpClient, stations);

        federatedBoards = new FederatedBoardService(federatedBoardRepo);
        var boardService = new BoardService(
                boardRepo,
                newStationMemberService(null, null),
                mock(MemberGroupService.class),
                mock(UserTagService.class));
        changes = new SharedBoardChangeService(
                boardService,
                federatedBoards,
                new FederatedBoardNotificationService(webhooks, federatedBoards, boardRepo));
    }

    private static int partner(UUID partnerStationUid, String remoteHost) {
        return Query.query("""
                        INSERT INTO federation_partner(station_id, partner_station_id, status, remote_host)
                        VALUES (:s, :p::uuid, 'ACTIVE', :host)
                        RETURNING id;
                        """)
                .single(Call.of()
                        .bind("s", station.id())
                        .bind("p", partnerStationUid, StandardValueConverter.UUID_STRING)
                        .bind("host", remoteHost))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    @AfterAll
    static void cleanup() {
        boardRepo.delete(board.id());
        stationRepo.delete(station.id());
        stationRepo.delete(localPartnerStation.id());
    }

    @BeforeEach
    void shareWithBoth() {
        federatedBoards.shareBoard(
                board.id(),
                List.of(
                        new PartnerShareConfig(remotePartnerId, BoardShareMode.FULL),
                        new PartnerShareConfig(localPartnerId, BoardShareMode.FULL)));
        reset(httpClient);
        when(httpClient.post(anyString(), any(), any(), any(), anyInt(), anyString()))
                .thenReturn(true);
    }

    private static void verifyOnlyDelivery(FederationRequest request, Object body) {
        verify(httpClient, after(SETTLE_MILLIS).times(1))
                .post(eq(REMOTE_HOST), eq(request), eq(body), any(), eq(station.id()), eq("private-key"));
        verify(httpClient, times(1)).post(any(), any(), any(), any(), anyInt(), any());
    }

    private static void verifyNothingDelivered() {
        verify(httpClient, after(SETTLE_MILLIS).never()).post(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void aRenameReachesTheRemotePartnerOnce() {
        String name = "Renamed " + UUID.randomUUID();

        changes.updateBoard(board.id(), name, "Desc", 0);

        verifyOnlyDelivery(
                RemoteBoardWebhookRoutes.BOARD_RENAMED.at(), new BoardRenamedPayload(board.uid(), name, "SHB"));
    }

    @Test
    void anUpdateThatKeepsTheNameTellsNobody() {
        var current = boardRepo.findById(board.id()).orElseThrow();

        changes.updateBoard(board.id(), current.name(), "Other description", 0);

        verifyNothingDelivered();
    }

    @Test
    void aChangedShareModeReachesTheRemotePartnerOnce() {
        changes.configureSharing(
                board.id(),
                List.of(
                        new PartnerShareConfig(remotePartnerId, BoardShareMode.READ_ONLY),
                        new PartnerShareConfig(localPartnerId, BoardShareMode.READ_ONLY)),
                List.of(StationUserType.MEMBER));

        verifyOnlyDelivery(
                RemoteBoardWebhookRoutes.SHARE_MODE_CHANGED.at(),
                new ShareModeChangedPayload(board.uid(), BoardShareMode.READ_ONLY));
    }

    @Test
    void droppingTheRemotePartnerTellsItOnce() {
        changes.configureSharing(
                board.id(), List.of(new PartnerShareConfig(localPartnerId, BoardShareMode.FULL)), List.of());

        verifyOnlyDelivery(RemoteBoardWebhookRoutes.BOARD_UNSHARED.at(), new BoardUnsharedPayload(board.uid()));
    }

    @Test
    void endingTheShareTellsTheRemotePartnerOnce() {
        changes.configureSharing(board.id(), List.of(), List.of());

        verifyOnlyDelivery(RemoteBoardWebhookRoutes.BOARD_UNSHARED.at(), new BoardUnsharedPayload(board.uid()));
    }

    @Test
    void droppingOnlyTheLocalPartnerSendsNothing() {
        changes.configureSharing(
                board.id(), List.of(new PartnerShareConfig(remotePartnerId, BoardShareMode.FULL)), List.of());

        verifyNothingDelivered();
    }

    @Test
    void anUnchangedConfigurationSendsNothing() {
        changes.configureSharing(
                board.id(),
                List.of(
                        new PartnerShareConfig(remotePartnerId, BoardShareMode.FULL),
                        new PartnerShareConfig(localPartnerId, BoardShareMode.FULL)),
                List.of());

        verifyNothingDelivered();
    }
}
