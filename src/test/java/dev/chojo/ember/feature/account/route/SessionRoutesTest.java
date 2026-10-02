/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.service.CrossStationDashboardService;
import dev.chojo.ember.feature.account.service.CrossStationDashboardService.CrossStationDashboard;
import dev.chojo.ember.feature.account.service.CrossStationDashboardService.CrossStationSummary;
import dev.chojo.ember.feature.account.service.SessionInfoService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.service.StationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The cross-station dashboard is the one the service builds for the reader's account.
 */
class SessionRoutesTest {
    private final CrossStationDashboardService dashboards = mock(CrossStationDashboardService.class);
    private final RouteHarness harness = RouteHarness.serving(new SessionRoutes(
            mock(SessionInfoService.class), mock(StationMemberService.class), mock(StationService.class), dashboards));

    @Test
    void theDashboardIsBuiltForTheReadersAccount() {
        when(dashboards.dashboard(TestSessions.ACCOUNT_ID))
                .thenReturn(new CrossStationDashboard(
                        List.of(new CrossStationSummary(UUID.randomUUID(), "Nord", 2, 1)), List.of()));
        var reader = harness.as(TestSessions.member(3, StationPermission.LOGIN));

        var response = harness.request(client -> client.get(PREFIX + "/session/cross-station-dashboard", reader));

        assertEquals(200, response.code());
        assertEquals(
                "Nord", json(response).get("stations").get(0).get("stationName").asString());
    }
}
