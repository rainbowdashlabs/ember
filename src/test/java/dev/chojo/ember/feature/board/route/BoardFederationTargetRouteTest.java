/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.LocalRouteServer;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.board.service.FederatedBoardNotificationService;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import dev.chojo.ember.feature.board.service.SharedBoardChangeService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Which partners a board is shared with, and which user type there may see it, is saved and read
 * back over HTTP through the API's own JSON mapper, which refuses any field the request does not
 * name. The body is the one the board settings page sends.
 */
class BoardFederationTargetRouteTest extends RepositoryTestBase {
    private static final String BOARD_KEY = "FTR";
    private static final String FEDERATION_PATH = "/boards/" + BOARD_KEY + "/federation";

    private static Station station;
    private static Station partnerStation;
    private static Account account;
    private static StationMember member;
    private static FederatedBoardService federatedBoardService;
    private static LocalRouteServer server;
    private static int boardId;
    private static int partnerId;

    @BeforeAll
    static void setupClass() {
        station = stationRepo.create("BoardFederationTargetStation");
        partnerStation = stationRepo.create("BoardFederationTargetPartner");
        account = accountRepo.create("board-federation-target@test.com", "Fiona", "Federation");
        member = stationMemberRepo.create(station.id(), account.id());
        boardId = boardRepo.create(station.id(), "Geteilt", "", BOARD_KEY).id();
        partnerId = Query.query("""
                        INSERT INTO federation_partner(station_id, partner_station_id, status)
                        VALUES (:station, :partner::uuid, 'ACTIVE')
                        RETURNING id;""")
                .single(Call.of()
                        .bind("station", station.id())
                        .bind("partner", partnerStation.uid(), StandardValueConverter.UUID_STRING))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();

        federatedBoardService = new FederatedBoardService(federatedBoardRepo);
        var boardService = new BoardService(
                boardRepo,
                mock(StationMemberService.class),
                mock(MemberGroupService.class),
                mock(UserTagService.class));
        var routes = new BoardRoutes(
                boardService,
                federatedBoardService,
                new SharedBoardChangeService(
                        boardService, federatedBoardService, mock(FederatedBoardNotificationService.class)),
                mock(StationMemberService.class),
                memberIdentityFactory,
                new BoardRouteGuards(boardService, mock(BoardTicketService.class), memberIdentityFactory));
        var session = new UserSession(
                account,
                1,
                station.id(),
                station.uid(),
                member,
                Set.of(StationPermission.BOARD_FEDERATE),
                Set.of(),
                null);
        server = LocalRouteServer.serving(stationRepo, clusterRepo, session, routes);
    }

    @AfterAll
    static void cleanupClass() {
        server.close();
        stationRepo.delete(station.id());
        stationRepo.delete(partnerStation.id());
        accountRepo.delete(account.id());
    }

    @AfterEach
    void unshare() {
        federatedBoardService.unshareBoard(boardId);
    }

    @Test
    void theRequiredUserTypeOfAPartnerTargetIsSavedAndReadBack() throws Exception {
        var saved = put("""
                {"targets": [{"partnerId": %d, "shareMode": "FULL", "requiredUserType": "TEAM"}],
                 "editUserTypes": ["MANAGER"]}""".formatted(partnerId));

        assertEquals(200, saved.statusCode(), saved.body());
        var config = LocalRouteServer.json(get());
        var target = config.get("targets").get(0);
        assertEquals(partnerId, target.get("partnerId").asInt());
        assertEquals(BoardShareMode.FULL.name(), target.get("shareMode").asString());
        assertEquals(StationUserType.TEAM.name(), target.get("requiredUserType").asString());
        assertEquals("MANAGER", config.get("editUserTypes").get(0).asString());
    }

    @Test
    void aTargetWithoutARequiredUserTypeIsOpenToEveryMember() throws Exception {
        var saved = put("""
                {"targets": [{"partnerId": %d, "shareMode": "READ_ONLY"}], "editUserTypes": []}""".formatted(partnerId));

        assertEquals(200, saved.statusCode(), saved.body());
        JsonNode target = LocalRouteServer.json(get()).get("targets").get(0);
        assertEquals(
                StationUserType.MEMBER.name(), target.get("requiredUserType").asString());
    }

    /** A field the request does not know is refused rather than dropped, so nothing is shared. */
    @Test
    void aTargetNamingARoleInsteadOfAUserTypeIsRefused() throws Exception {
        var saved = put("""
                {"targets": [{"partnerId": %d, "shareMode": "FULL", "requiredRole": "TEAM"}], "editUserTypes": []}""".formatted(partnerId));

        assertNotEquals(200, saved.statusCode());
        assertTrue(federatedBoardService.findShareTargets(boardId).isEmpty());
    }

    private static HttpResponse<String> put(String body) throws Exception {
        return server.put(FEDERATION_PATH, body);
    }

    private static HttpResponse<String> get() throws Exception {
        return server.get(FEDERATION_PATH);
    }
}
