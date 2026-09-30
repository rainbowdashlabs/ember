/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.statistics.entity.AdminOverview;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics;
import dev.chojo.ember.feature.statistics.entity.StationStatistics;
import dev.chojo.ember.feature.statistics.service.StatisticsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatisticsRoutesTest {
    @Test
    void aStationSeesItsOwnFiguresAndAnAdministratorTheInstances() {
        var statistics = mock(StatisticsService.class);
        when(statistics.forStation(3))
                .thenReturn(new StationStatistics(1, Map.of(), List.of(), List.of(), List.of(), Map.of()));
        when(statistics.forInstance())
                .thenReturn(
                        new AdminStatistics(null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        when(statistics.overview()).thenReturn(new AdminOverview(0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of()));
        var harness = RouteHarness.serving(new StatisticsRoutes(statistics));

        harness.run((server, client) -> {
            var member = harness.as(TestSessions.member(3, StationPermission.STATION_STATISTICS));
            assertEquals(200, client.get(PREFIX + "/statistics", member).code());
            var admin = harness.as(TestSessions.administrator());
            assertEquals(200, client.get(PREFIX + "/admin/statistics", admin).code());
            assertEquals(200, client.get(PREFIX + "/admin/overview", admin).code());
            assertEquals(403, client.get(PREFIX + "/admin/overview", member).code());
        });

        verify(statistics).forStation(3);
        verify(statistics).forInstance();
        verify(statistics).overview();
    }
}
