/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.feature.mail.service.MailWebhookService;
import dev.chojo.ember.feature.mail.service.MailWebhookService.SignedCall;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import tools.jackson.databind.JsonNode;

/**
 * Where a mail provider reports what became of the messages it took from us.
 *
 * <p>The endpoint is public because the provider calls it without a session of ours. What stands in
 * the way of a stranger is the webhook key in the address. A key in a URL is not ideal, because URLs
 * reach access logs. It is what providers support, and accepting unauthenticated reports would be
 * worse: anybody could mark anyone's mail as bounced.
 */
@Singleton
public class MailWebhookRoutes implements Routes {
    private static final String KEY_SCOPES_THE_REPORT =
            "the webhook key in the address says whose mail is reported on, the instance's or one station's,"
                    + " and the report is scoped to what the key authorises";

    private final MailWebhookService webhooks;

    @Inject
    public MailWebhookRoutes(MailWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/public/webhooks/{key}/mail/brevo", this::brevoEvent);
        routes.post(prefix + "/public/webhooks/{key}/mail/sendgrid", this::sendGridEvents);
        routes.post(prefix + "/public/webhooks/{key}/mail/sweego", this::sweegoEvent);
    }

    @StationFree(KEY_SCOPES_THE_REPORT)
    private void sweegoEvent(Context ctx) {
        webhooks.sweego(
                ctx.pathParam("key"),
                new SignedCall(
                        ctx.header("webhook-id"),
                        ctx.header("webhook-timestamp"),
                        ctx.header("webhook-signature"),
                        ctx.body(),
                        ctx.bodyAsClass(JsonNode.class)));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @StationFree(KEY_SCOPES_THE_REPORT)
    private void sendGridEvents(Context ctx) {
        webhooks.sendGrid(ctx.pathParam("key"), ctx.bodyAsClass(JsonNode.class));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Answers {@code 202} for a delivery outcome that matched none of our mails, so the provider
     * stops retrying while the difference stays visible in its own log.
     */
    @StationFree(KEY_SCOPES_THE_REPORT)
    private void brevoEvent(Context ctx) {
        boolean settled = webhooks.brevo(ctx.pathParam("key"), ctx.bodyAsClass(JsonNode.class));
        ctx.status(settled ? HttpStatus.NO_CONTENT : HttpStatus.ACCEPTED);
    }
}
