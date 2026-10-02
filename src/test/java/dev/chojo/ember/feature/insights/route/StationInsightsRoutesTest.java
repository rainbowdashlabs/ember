/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.insights.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.InsightsRefusal;
import dev.chojo.ember.feature.insights.service.PageInsightsService;
import dev.chojo.ember.feature.insights.service.PageInsightsService.LeaderboardResponse;
import dev.chojo.ember.feature.insights.service.PageInsightsService.PageDetailResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StationInsightsRoutesTest {
    private static final String SPAN = "?from=2026-09-01T00:00:00Z&to=2026-09-02T00:00:00Z";
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-02T00:00:00Z");

    @Test
    void theStationsPagesAreRankedAndOneIsBrokenDown() {
        var insights = mock(PageInsightsService.class);
        when(insights.leaderboard(anyInt(), any(), any(), anyInt())).thenReturn(new LeaderboardResponse(List.of()));
        when(insights.pageDetail(anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageDetailResponse(List.of(), List.of(), List.of(), List.of()));
        var harness = RouteHarness.serving(new StationInsightsRoutes(insights));

        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(3, StationPermission.STATION_ADMINISTRATOR));
            assertEquals(
                    200,
                    client.get(PREFIX + "/station/insights/pages" + SPAN + "&limit=10", manager)
                            .code());
            assertEquals(
                    200,
                    client.get(PREFIX + "/station/insights/pages/8" + SPAN, manager)
                            .code());
            assertEquals(
                    InsightsRefusal.INSIGHTS_PAGE_NOT_A_NUMBER,
                    refusalOf(client.get(PREFIX + "/station/insights/pages/x" + SPAN, manager)));
            assertEquals(
                    InsightsRefusal.INSIGHTS_PAGE_WINDOW_ENDS_BEFORE_IT_STARTS,
                    refusalOf(client.get(
                            PREFIX + "/station/insights/pages/8?from=2026-09-02T00:00:00Z&to=2026-09-01T00:00:00Z",
                            manager)));
        });

        verify(insights).leaderboard(3, FROM, TO, 10);
        verify(insights).pageDetail(3, 8, FROM, TO);
    }
}
