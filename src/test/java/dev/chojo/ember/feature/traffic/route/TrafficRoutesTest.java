/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.traffic.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.traffic.entity.AuthBucket;
import dev.chojo.ember.feature.traffic.service.TrafficReportService;
import dev.chojo.ember.feature.traffic.service.TrafficReportService.HourlyTrafficResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The hourly traffic, for the whole instance and for one station, over HTTP.
 */
class TrafficRoutesTest {
    private static final String SPAN = "?from=2026-09-01T00:00:00Z&to=2026-09-02T00:00:00Z";
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-02T00:00:00Z");

    @Test
    void anAdministratorReadsAnyStationsTrafficOfAnyKind() {
        var traffic = mock(TrafficReportService.class);
        when(traffic.hourly(any(), any(), any(), any())).thenReturn(new HourlyTrafficResponse(List.of()));
        var harness = RouteHarness.serving(new AdminTrafficRoutes(traffic));
        var auth = AuthBucket.values()[0];

        harness.run((server, client) -> {
            var admin = harness.as(TestSessions.administrator());
            assertEquals(
                    200,
                    client.get(PREFIX + "/admin/traffic/hourly" + SPAN, admin).code());
            assertEquals(
                    200,
                    client.get(PREFIX + "/admin/traffic/hourly" + SPAN + "&stationId=4&auth=" + auth, admin)
                            .code());
            assertEquals(Refusal.TRAFFIC_SPAN_MISSING, refusalOf(client.get(PREFIX + "/admin/traffic/hourly", admin)));
            assertEquals(
                    Refusal.TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS,
                    refusalOf(client.get(
                            PREFIX + "/admin/traffic/hourly?from=2026-09-02T00:00:00Z&to=2026-09-01T00:00:00Z",
                            admin)));
        });

        verify(traffic).hourly(FROM, TO, null, null);
        verify(traffic).hourly(FROM, TO, 4, auth);
    }

    @Test
    void aStationReadsOnlyItsOwnTraffic() {
        var traffic = mock(TrafficReportService.class);
        when(traffic.hourly(any(), any(), any(), any())).thenReturn(new HourlyTrafficResponse(List.of()));
        var harness = RouteHarness.serving(new StationTrafficRoutes(traffic));

        var answer = harness.request(client -> client.get(
                PREFIX + "/station/traffic/hourly" + SPAN,
                harness.as(TestSessions.member(3, StationPermission.STATION_ADMINISTRATOR))));

        assertEquals(200, answer.code());
        verify(traffic).hourly(FROM, TO, 3, null);
    }
}
