/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.feature.mail.service.MailWebhookService;
import dev.chojo.ember.feature.mail.service.MailWebhookService.SignedCall;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailWebhookRoutesTest {
    private static final String HOOKS = PREFIX + "/public/webhooks/k1/mail/";

    @Test
    void aSettledBrevoReportIsAnsweredWithNoContentAndAnUnmatchedOneAsAccepted() {
        var webhooks = mock(MailWebhookService.class);
        when(webhooks.brevo(eq("k1"), any())).thenReturn(true, false);
        var harness = RouteHarness.serving(new MailWebhookRoutes(webhooks));

        harness.run((server, client) -> {
            assertEquals(
                    204,
                    client.post(HOOKS + "brevo", body("{\"event\": \"delivered\"}"))
                            .code());
            assertEquals(
                    202,
                    client.post(HOOKS + "brevo", body("{\"event\": \"delivered\"}"))
                            .code());
            assertEquals(204, client.post(HOOKS + "sendgrid", body("[]")).code());
        });

        verify(webhooks).sendGrid(eq("k1"), any());
    }

    @Test
    void aSweegoReportReachesTheServiceWithItsSignatureAndItsBodyAsSent() {
        var webhooks = mock(MailWebhookService.class);
        var harness = RouteHarness.serving(new MailWebhookRoutes(webhooks));

        var answer = harness.request(client ->
                client.post(HOOKS + "sweego", body("{\"event_type\": \"delivered\"}"), request -> request.header(
                                "webhook-id", "id1")
                        .header("webhook-timestamp", "1769696506")
                        .header("webhook-signature", "v1,abc")));

        assertEquals(204, answer.code());
        var call = ArgumentCaptor.forClass(SignedCall.class);
        verify(webhooks).sweego(eq("k1"), call.capture());
        assertEquals("id1", call.getValue().id());
        assertEquals("v1,abc", call.getValue().signature());
        assertEquals("delivered", call.getValue().body().path("event_type").asString());
    }
}
