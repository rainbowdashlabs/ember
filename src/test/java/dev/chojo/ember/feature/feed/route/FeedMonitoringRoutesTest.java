/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.feed.entity.FeedUserAgentStat;
import dev.chojo.ember.feature.feed.service.FeedMetricsService;
import dev.chojo.ember.feature.feed.service.FeedUseService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What an administrator and a station see about the feeds, over HTTP.
 */
class FeedMonitoringRoutesTest {
    @Test
    void theReaderAgentsComeWithTheTotalOfAllRequests() {
        var metrics = mock(FeedMetricsService.class);
        when(metrics.totalRequests()).thenReturn(40L);
        when(metrics.topUserAgents(5))
                .thenReturn(List.of(new FeedUserAgentStat("h", "cal/1", 3, Instant.EPOCH, Instant.EPOCH)));
        var harness = RouteHarness.serving(new FeedMetricsRoutes(metrics));

        harness.run((server, client) -> {
            var admin = harness.as(TestSessions.administrator());
            var agents = json(client.get(PREFIX + "/admin/feed-metrics/user-agents?limit=5", admin));
            assertEquals(40, agents.path("totalRequests").asInt());
            assertEquals(
                    "cal/1", agents.path("userAgents").get(0).path("uaString").asString());
            assertEquals(
                    200,
                    client.get(PREFIX + "/admin/feed-metrics?days=7", admin).code());
        });

        verify(metrics).recentDailyMetrics(7);
    }

    @Test
    void aStationSeesItsOwnMembersSubscriptions() {
        var feedUse = mock(FeedUseService.class);
        var harness = RouteHarness.serving(new StationFeedUseRoutes(feedUse));

        var answer = harness.request(client -> client.get(
                PREFIX + "/station/monitoring/feeds",
                harness.as(TestSessions.member(3, StationPermission.STATION_ADMINISTRATOR))));

        assertEquals(200, answer.code());
        verify(feedUse).forStation(3);
    }
}
