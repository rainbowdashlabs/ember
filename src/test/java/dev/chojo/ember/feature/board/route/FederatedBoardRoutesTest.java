/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.FederationBoardBookmark;
import dev.chojo.ember.feature.board.service.FederatedBoardAccessService;
import dev.chojo.ember.feature.board.service.FederatedBoardDiscoveryService;
import dev.chojo.ember.feature.board.service.FederatedBoardDiscoveryService.FederatedBoardDetail;
import dev.chojo.ember.feature.board.service.FederatedBoardLocator;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import dev.chojo.ember.feature.board.service.FederatedBoardStructureProxy;
import dev.chojo.ember.feature.board.service.FederatedTicketDetailProxy;
import dev.chojo.ember.feature.board.service.FederatedTicketProxy;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationService;
import io.javalin.testtools.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A partner's board is reached through the partner row the station holds for it, and a bookmark
 * names the partner station it points at.
 */
class FederatedBoardRoutesTest {
    private static final int STATION = 3;
    private static final UUID PARTNER_UID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID BOARD_UID = UUID.fromString("00000000-0000-0000-0000-000000000055");
    private static final FederationPartner PARTNER = new FederationPartner(
            7,
            STATION,
            PARTNER_UID,
            null,
            null,
            null,
            FederationPartner.FederationStatus.ACTIVE,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            null,
            "Wache Nord");
    private static final FederationBoardBookmark BOOKMARK = new FederationBoardBookmark(
            1, TestSessions.MEMBER_ID, 7, BOARD_UID, "Einsatz", "EIN", BoardShareMode.FULL, Instant.EPOCH);

    private FederatedBoardService boards;
    private FederatedBoardDiscoveryService discovery;
    private FederationService federation;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        boards = mock(FederatedBoardService.class);
        discovery = mock(FederatedBoardDiscoveryService.class);
        federation = mock(FederationService.class);
        when(federation.findPartnerByRemoteUid(STATION, PARTNER_UID)).thenReturn(Optional.of(PARTNER));
        when(federation.findPartner(7)).thenReturn(Optional.of(PARTNER));
        harness = RouteHarness.serving(new FederatedBoardRoutes(
                boards,
                mock(FederatedBoardAccessService.class),
                discovery,
                mock(FederatedBoardStructureProxy.class),
                mock(FederatedTicketProxy.class),
                mock(FederatedTicketDetailProxy.class),
                mock(FederatedBoardLocator.class),
                federation));
    }

    private Consumer<Request.Builder> user() {
        return harness.as(TestSessions.member(STATION, StationPermission.BOARD_USE));
    }

    @Test
    void aPartnersBoardIsAskedOfThePartnerRowTheStationHolds() {
        when(discovery.proxyGetBoard(7, "EIN"))
                .thenReturn(new FederatedBoardDetail(null, BoardShareMode.FULL, "Wache Nord"));

        var answer =
                harness.request(client -> client.get(PREFIX + "/federated/boards/" + PARTNER_UID + "/EIN", user()));

        assertEquals("Wache Nord", json(answer).path("stationName").asString());
    }

    @Test
    void aStationThatIsNoPartnerHasNoBoardToShow() {
        var stranger = UUID.fromString("00000000-0000-0000-0000-000000000098");

        var answer = harness.request(client -> client.get(PREFIX + "/federated/boards/" + stranger + "/EIN", user()));

        assertEquals(Refusal.FEDERATION_PARTNER_NOT_HERE_FOR_BOARD, refusalOf(answer));
    }

    @Test
    void bookmarksNameThePartnerStationTheyPointAt() {
        when(boards.findBookmarks(TestSessions.MEMBER_ID)).thenReturn(List.of(BOOKMARK));

        var answer = harness.request(client -> client.get(PREFIX + "/federated/boards/bookmarks", user()));

        assertEquals(
                PARTNER_UID.toString(),
                json(answer).path(0).path("partnerStationUid").asString());
    }

    @Test
    void aBookmarkIsSetOnlyForAPartner() {
        when(boards.createBookmark(TestSessions.MEMBER_ID, 7, BOARD_UID, "Einsatz", "EIN", BoardShareMode.FULL))
                .thenReturn(BOOKMARK);
        var stranger = UUID.fromString("00000000-0000-0000-0000-000000000098");
        String bookmark = "{\"partnerUid\": \"%s\", \"remoteBoardUid\": \"%s\", \"remoteBoardName\": \"Einsatz\","
                + " \"remoteBoardShortKey\": \"EIN\", \"shareMode\": \"FULL\"}";

        harness.run((server, client) -> {
            assertEquals(
                    200,
                    client.post(
                                    PREFIX + "/federated/boards/bookmarks",
                                    body(bookmark.formatted(PARTNER_UID, BOARD_UID)),
                                    user())
                            .code());
            assertEquals(
                    Refusal.FEDERATION_PARTNER_NOT_HERE_FOR_BOOKMARK,
                    refusalOf(client.post(
                            PREFIX + "/federated/boards/bookmarks",
                            body(bookmark.formatted(stranger, BOARD_UID)),
                            user())));
        });

        verify(boards).createBookmark(TestSessions.MEMBER_ID, 7, BOARD_UID, "Einsatz", "EIN", BoardShareMode.FULL);
    }
}
