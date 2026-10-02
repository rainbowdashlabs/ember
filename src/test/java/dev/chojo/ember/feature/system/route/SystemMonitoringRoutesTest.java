/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.system.entity.ProblemReport;
import dev.chojo.ember.feature.system.service.MonitoringCountService;
import dev.chojo.ember.feature.system.service.MonitoringCountService.MonitoringCounts;
import dev.chojo.ember.feature.system.service.ProblemReportService;
import dev.chojo.ember.feature.system.service.ProblemReportService.ReportRequest;
import dev.chojo.ember.feature.system.service.SitemapService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The monitoring counts, the problem reports and the sitemaps, over HTTP.
 */
class SystemMonitoringRoutesTest {
    private static final UUID STATION = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private static ProblemReport report(int id) {
        return new ProblemReport(
                id, 3, 11, "Mara", "It broke", "/x", "[]", "[]", "ua", "1x1", null, false, null, null, null);
    }

    @Test
    void theCountsAnswerAnAdministrator() {
        var counts = mock(MonitoringCountService.class);
        when(counts.counts()).thenReturn(new MonitoringCounts(1, 2, true, 3, 4));
        var harness = RouteHarness.serving(new AdminMonitoringCountRoutes(counts));

        var answer = harness.request(
                client -> client.get(PREFIX + "/admin/monitoring-counts", harness.as(TestSessions.administrator())));

        assertEquals(4, json(answer).path("beaconReports").asInt());
    }

    @Test
    void aMemberReportsAProblemAndAnAdministratorWorksThroughThem() {
        var reports = mock(ProblemReportService.class);
        when(reports.submit(any(Integer.class), any(), any(), any())).thenReturn(report(5));
        when(reports.list(true)).thenReturn(List.of(report(5)));
        when(reports.acknowledgeAll()).thenReturn(2);
        when(reports.picture(5)).thenReturn(new MediaContent(new byte[] {1, 2, 3}, "image/png"));
        var harness = RouteHarness.serving(new ProblemReportRoutes(reports));

        harness.run((server, client) -> {
            var admin = harness.as(TestSessions.administrator());
            var created = client.post(
                    PREFIX + "/problem-reports",
                    body("""
                            {"message": "It broke", "pageUrl": "/x", "userRoles": "[]", "recentRequests": "[]",
                             "browserInfo": "ua", "screenSize": "1x1", "screenshot": null}"""),
                    harness.as(TestSessions.member(3, StationPermission.LOGIN)));
            assertEquals(201, created.code());
            assertEquals(
                    1,
                    json(client.get(PREFIX + "/admin/problem-reports?includeAcknowledged=true", admin))
                            .size());
            assertEquals(
                    204,
                    client.post(PREFIX + "/admin/problem-reports/5/acknowledge", null, admin)
                            .code());
            assertEquals(
                    2,
                    json(client.post(PREFIX + "/admin/problem-reports/acknowledge-all", null, admin))
                            .path("acknowledged")
                            .asInt());
            var picture = client.get(PREFIX + "/admin/problem-reports/5/screenshot", admin);
            assertTrue(header(picture, "Content-Type").startsWith("image/png"));
            assertEquals(
                    204,
                    client.delete(PREFIX + "/admin/problem-reports/5", null, admin)
                            .code());
        });

        verify(reports)
                .submit(
                        eq(3),
                        eq(TestSessions.MEMBER_ID),
                        any(),
                        eq(new ReportRequest("It broke", "/x", "[]", "[]", "ua", "1x1", null)));
        verify(reports).acknowledge(5);
        verify(reports).delete(5);
    }

    @Test
    void theSitemapsAreServedAsXmlAndAMalformedStationIsNotHere() {
        var sitemaps = mock(SitemapService.class);
        when(sitemaps.index()).thenReturn("<sitemapindex/>");
        when(sitemaps.forStation(STATION)).thenReturn("<urlset/>");
        var harness = RouteHarness.serving(new SitemapRoutes(sitemaps));

        harness.run((server, client) -> {
            var index = client.get("/sitemap.xml");
            assertEquals("<sitemapindex/>", index.body().string());
            assertEquals("public, max-age=21600", header(index, "Cache-Control"));
            assertEquals(
                    "<urlset/>",
                    client.get("/sitemap-station-" + STATION + ".xml").body().string());
            assertEquals(
                    GeneralRefusal.ADDRESS_NOT_AN_IDENTIFIER, refusalOf(client.get("/sitemap-station-not-a-uid.xml")));
        });
    }
}
