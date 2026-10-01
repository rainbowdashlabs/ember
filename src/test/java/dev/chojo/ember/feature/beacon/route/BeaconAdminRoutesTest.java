/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.feature.beacon.entity.BeaconPayloads.MetricsBatch;
import dev.chojo.ember.feature.beacon.entity.BeaconPayloads.ProblemPayload;
import dev.chojo.ember.feature.beacon.entity.BeaconPayloads.ReportPayload;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService.BeaconSettingsRequest;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService.BeaconStatus;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService.SendReportRequest;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService.SendResult;
import dev.chojo.ember.feature.media.entity.MediaContent;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BeaconAdminRoutesTest {
    private static final String BASE = PREFIX + "/admin/beacon";

    private BeaconAdminService beacon;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        beacon = mock(BeaconAdminService.class);
        when(beacon.previewProblem(anyLong()))
                .thenReturn(new ProblemPayload(null, "1", null, null, "f", "ERROR", "l", "E", "m", "", 1, null, null));
        when(beacon.previewReport(anyInt()))
                .thenReturn(new ReportPayload(null, "1", null, null, "m", "/p", "b", "s", "r", "q", null, null));
        when(beacon.previewMetrics()).thenReturn(new MetricsBatch(1, "1", "2026-09-01", List.of()));
        harness = RouteHarness.serving(new BeaconAdminRoutes(beacon));
    }

    private Response get(HttpClient client, String path) {
        return client.get(BASE + path, harness.as(TestSessions.administrator()));
    }

    private Response post(HttpClient client, String path, Object json) {
        return client.post(BASE + path, json, harness.as(TestSessions.administrator()));
    }

    @Test
    void theStatusIsReadAndTheSettingsWritten() {
        var status = new BeaconStatus(true, "https://b.test", true, false, true, false, true, "Mara", "m@test");
        when(beacon.status()).thenReturn(status);
        when(beacon.update(any())).thenReturn(status);

        harness.run((server, client) -> {
            assertEquals("https://b.test", json(get(client, "")).path("url").asString());
            var saved = client.put(BASE, body("""
                            {"enabled": true, "url": "https://b.test", "forwardProblems": true, "forwardReports": false,
                             "reviewReportPictures": true, "metricsEnabled": false, "receiving": true,
                             "contactName": "Mara", "contactMail": "m@test"}"""), harness.as(TestSessions.administrator()));
            assertEquals(200, saved.code());
        });

        verify(beacon)
                .update(new BeaconSettingsRequest(
                        true, "https://b.test", true, false, true, false, true, "Mara", "m@test"));
    }

    @Test
    void problemsAreNamedByNumberPreviewedAndSent() {
        when(beacon.sendProblem(anyLong())).thenReturn(new SendResult(1));
        when(beacon.sendProblems(any())).thenReturn(new SendResult(2));

        harness.run((server, client) -> {
            assertEquals(200, get(client, "/problems/7/preview").code());
            assertEquals(
                    1,
                    json(post(client, "/problems/7/send", null)).path("queued").asInt());
            assertEquals(
                    2,
                    json(post(client, "/problems/send", body("{\"ids\": [1, 2]}")))
                            .path("queued")
                            .asInt());
            assertEquals(Refusal.BEACON_PROBLEM_ID_NOT_A_NUMBER, refusalOf(get(client, "/problems/x/preview")));
        });

        verify(beacon).previewProblem(7L);
        verify(beacon).sendProblems(List.of(1L, 2L));
    }

    @Test
    void aReportIsSentAsItStandsOrWithTheOperatorsDecision() {
        when(beacon.sendReport(anyInt(), any())).thenReturn(new SendResult(1));

        harness.run((server, client) -> {
            assertEquals(200, get(client, "/reports/5/preview").code());
            assertEquals(
                    1,
                    json(post(client, "/reports/5/send", null)).path("queued").asInt());
            post(client, "/reports/5/send", body("{\"screenshot\": null, \"dropScreenshot\": true}"));
            assertEquals(200, get(client, "/figures/preview").code());
            assertEquals(Refusal.BEACON_ID_NOT_A_NUMBER, refusalOf(post(client, "/reports/x/send", null)));
        });

        verify(beacon).previewReport(5);
        verify(beacon).sendReport(5, SendReportRequest.AS_IT_STANDS);
        verify(beacon).sendReport(5, new SendReportRequest(null, true));
        verify(beacon).previewMetrics();
    }

    @Test
    void whatWasGatheredIsListedResolvedAcknowledgedAndShown() {
        when(beacon.collectedPicture(4)).thenReturn(new MediaContent(new byte[] {1}, "image/png"));

        harness.run((server, client) -> {
            assertEquals(
                    200,
                    get(client, "/collected/faults?includeAcknowledged=true").code());
            var resolved = client.put(
                    BASE + "/collected/faults/3",
                    body("{\"acknowledged\": true, \"resolvedIn\": \"1.2.4\"}"),
                    harness.as(TestSessions.administrator()));
            assertEquals(204, resolved.code());
            assertEquals(200, get(client, "/collected/reports").code());
            assertEquals(
                    204, post(client, "/collected/reports/4/acknowledge", null).code());
            assertTrue(header(get(client, "/collected/reports/4/screenshot"), "Content-Type")
                    .startsWith("image/png"));
            assertEquals(200, get(client, "/collected/figures?days=7").code());
            assertEquals(200, get(client, "/collected/figures").code());
        });

        verify(beacon).faults(true);
        verify(beacon).resolveFault(3, true, "1.2.4");
        verify(beacon).collectedReports(false);
        verify(beacon).acknowledgeReport(4);
        verify(beacon).collectedMetrics(7);
        verify(beacon).collectedMetrics(30);
    }
}
