/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.NameChangeRequest;
import dev.chojo.ember.feature.members.service.NameChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Name requests over HTTP: decided at the caller's station, and taken back by their own account. */
class NameChangeRoutesTest {
    private static final int STATION_ID = 3;

    private NameChangeService nameChanges;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        nameChanges = mock(NameChangeService.class);
        harness = RouteHarness.serving(new NameChangeRoutes(nameChanges));
    }

    private static dev.chojo.ember.api.UserSession manager() {
        return TestSessions.member(STATION_ID, StationPermission.MEMBER_CHANGES);
    }

    @Test
    void aManagerSeesTheStationsOpenRequests() {
        when(nameChanges.openAt(any(Integer.class), any())).thenReturn(List.of());

        harness.run((server, client) -> assertEquals(
                200,
                client.get(PREFIX + "/name-change-requests", harness.as(manager()))
                        .code()));
    }

    @Test
    void aDecisionIsTakenAtTheCallersStationByTheirAccount() {
        harness.run((server, client) -> {
            assertEquals(
                    204,
                    client.post(PREFIX + "/name-change-requests/5/approve", null, harness.as(manager()))
                            .code());
            assertEquals(
                    204,
                    client.post(
                                    PREFIX + "/name-change-requests/6/deny",
                                    body("{\"reason\":\"Bitte mit Ausweis\"}"),
                                    harness.as(manager()))
                            .code());
        });

        verify(nameChanges).approve(STATION_ID, TestSessions.ACCOUNT_ID, 5);
        verify(nameChanges).deny(STATION_ID, TestSessions.ACCOUNT_ID, 6, "Bitte mit Ausweis");
    }

    @Test
    void aMemberWithoutTheRightCannotDecide() {
        harness.run((server, client) -> assertEquals(
                403,
                client.post(
                                PREFIX + "/name-change-requests/5/approve",
                                null,
                                harness.as(TestSessions.member(STATION_ID)))
                        .code()));
    }

    @Test
    void aMemberSeesAndTakesBackTheirOwnRequest() {
        when(nameChanges.openOf(TestSessions.ACCOUNT_ID))
                .thenReturn(Optional.of(new NameChangeRequest(
                        5, TestSessions.ACCOUNT_ID, "Mia", "Neu", Instant.EPOCH, null, null, null, null)));

        harness.run((server, client) -> {
            var own = json(
                    client.get(PREFIX + "/account/name-change-request", harness.as(TestSessions.member(STATION_ID))));
            assertEquals("Mia", own.get("pending").get("firstName").asString());
            assertEquals(
                    204,
                    client.delete(
                                    PREFIX + "/account/name-change-request",
                                    null,
                                    harness.as(TestSessions.member(STATION_ID)))
                            .code());
        });

        verify(nameChanges).withdraw(TestSessions.ACCOUNT_ID);
    }

    @Test
    void nothingWaitingAnswersAnEmptyRequest() {
        when(nameChanges.openOf(TestSessions.ACCOUNT_ID)).thenReturn(Optional.empty());

        harness.run((server, client) -> assertTrue(
                json(client.get(PREFIX + "/account/name-change-request", harness.as(TestSessions.member(STATION_ID))))
                        .get("pending")
                        .isNull()));
    }
}
