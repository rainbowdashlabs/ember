/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.accountlink.entity.LinkOrigin;
import dev.chojo.ember.feature.accountlink.entity.LinkPrompt;
import dev.chojo.ember.feature.accountlink.entity.LinkState;
import dev.chojo.ember.feature.accountlink.entity.LinkStatus;
import dev.chojo.ember.feature.accountlink.service.AccountLinkService;
import dev.chojo.ember.feature.accountlink.service.LinkAnswerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Link requests over HTTP: the person answers with their own session, and the station reads and sends
 * again only for its own members.
 */
class AccountLinkRoutesTest {
    private static final int STATION_ID = 3;
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-00000000a11c");

    private LinkAnswerService answers;
    private AccountLinkService links;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        answers = mock(LinkAnswerService.class);
        links = mock(AccountLinkService.class);
        harness = RouteHarness.serving(new AccountLinkRoutes(answers), new MemberLinkRoutes(links));
    }

    private static LinkPrompt prompt() {
        return new LinkPrompt(
                REQUEST, "Wache Nord", "Anna", null, null, LinkOrigin.INVITE, "Ina", Instant.EPOCH, Instant.EPOCH);
    }

    private static LinkState waiting() {
        return new LinkState(LinkStatus.WAITING, LinkOrigin.INVITE, Instant.EPOCH, Instant.EPOCH, null, Instant.EPOCH);
    }

    @Test
    void aSignedInPersonSeesTheirRequestsAndAnswersThemAsThemselves() {
        when(answers.waitingFor(TestSessions.ACCOUNT_ID)).thenReturn(List.of(prompt()));
        var person = TestSessions.member(STATION_ID);

        harness.run((server, client) -> {
            var listed = json(client.get(PREFIX + "/account/link-requests", harness.as(person)));
            assertEquals("Wache Nord", listed.get(0).path("stationName").asString());
            assertEquals(
                    200,
                    client.post(PREFIX + "/account/link-requests/" + REQUEST + "/accept", null, harness.as(person))
                            .code());
            assertEquals(
                    200,
                    client.post(PREFIX + "/account/link-requests/" + REQUEST + "/decline", null, harness.as(person))
                            .code());
        });

        verify(answers).accept(eq(TestSessions.ACCOUNT_ID), eq(REQUEST), any(), any());
        verify(answers).decline(TestSessions.ACCOUNT_ID, REQUEST);
    }

    @Test
    void theMailedLinkOpensTheRequestForTheSessionAndNothingElse() {
        when(answers.opened(TestSessions.ACCOUNT_ID, "mailed-token")).thenReturn(prompt());
        when(answers.opened(TestSessions.ACCOUNT_ID, "wrong-token"))
                .thenThrow(MemberRefusal.LINK_REQUEST_NOT_OPEN.raise());
        var person = TestSessions.member(STATION_ID);

        harness.run((server, client) -> {
            assertEquals(
                    REQUEST.toString(),
                    json(client.get(PREFIX + "/account/link-requests/by-token/mailed-token", harness.as(person)))
                            .path("uid")
                            .asString());
            assertEquals(
                    MemberRefusal.LINK_REQUEST_NOT_OPEN,
                    refusalOf(client.get(PREFIX + "/account/link-requests/by-token/wrong-token", harness.as(person))));
            assertEquals(
                    401,
                    client.get(PREFIX + "/account/link-requests/by-token/mailed-token")
                            .code());
        });

        verify(answers, never()).accept(anyInt(), any(), any(), any());
    }

    @Test
    void aMalformedRequestIsNoIdentifier() {
        harness.run((server, client) -> assertEquals(
                GeneralRefusal.ADDRESS_NOT_AN_IDENTIFIER,
                refusalOf(client.post(
                        PREFIX + "/account/link-requests/not-a-uid/accept",
                        null,
                        harness.as(TestSessions.member(STATION_ID))))));
    }

    @Test
    void theStationReadsAndSendsAgainForItsOwnMembers() {
        when(links.statesAt(STATION_ID)).thenReturn(Map.of(7, waiting()));
        when(links.stateOf(STATION_ID, 7)).thenReturn(Optional.of(waiting()));
        when(links.stateOf(STATION_ID, 8)).thenReturn(Optional.empty());
        when(links.sendAgain(STATION_ID, 7, TestSessions.MEMBER_ID)).thenReturn(waiting());
        var manager = TestSessions.member(STATION_ID, StationPermission.MEMBER_READ, StationPermission.MEMBER_EDIT);

        harness.run((server, client) -> {
            var all = json(client.get(PREFIX + "/member-links", harness.as(manager)));
            assertEquals(7, all.get(0).path("memberId").asInt());
            assertEquals(
                    "WAITING",
                    json(client.get(PREFIX + "/member-links/7", harness.as(manager)))
                            .path("link")
                            .path("status")
                            .asString());
            assertTrue(json(client.get(PREFIX + "/member-links/8", harness.as(manager)))
                    .path("link")
                    .isNull());
            assertEquals(
                    200,
                    client.post(PREFIX + "/member-links/7/send-again", null, harness.as(manager))
                            .code());
        });

        verify(links).sendAgain(STATION_ID, 7, TestSessions.MEMBER_ID);
    }

    @Test
    void readingIsNotSendingAgain() {
        var reader = TestSessions.member(STATION_ID, StationPermission.MEMBER_READ);

        harness.run((server, client) -> assertEquals(
                403,
                client.post(PREFIX + "/member-links/7/send-again", null, harness.as(reader))
                        .code()));

        verify(links, never()).sendAgain(anyInt(), anyInt(), anyInt());
    }
}
