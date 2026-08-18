/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.mail.entity.MailDeliveryStatus;
import dev.chojo.ember.feature.mail.service.MailChainService;
import dev.chojo.ember.feature.mail.service.MailDeliveryService;
import dev.chojo.ember.feature.mail.service.SweegoSignature;
import dev.chojo.ember.feature.webhook.service.WebhookKeyService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Where a mail provider reports what became of the messages it took from us.
 *
 * <p>The endpoint is public because the provider calls it without a session of ours. What stands in
 * the way of a stranger is the webhook key in the address, which also decides what the caller may
 * touch: the instance key answers for all mail, a station key only for that station's. Brevo does
 * not sign its calls, so the key in the address is what there is.
 *
 * <p>A key in a URL is not ideal, because URLs reach access logs. It is what providers support, and
 * accepting unauthenticated reports would be worse: anybody could mark anyone's mail as bounced.
 */
@Singleton
public class MailWebhookRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(MailWebhookRoutes.class);

    private final MailDeliveryService deliveryService;
    private final WebhookKeyService keyService;
    private final MailChainService chainService;

    @Inject
    public MailWebhookRoutes(
            MailDeliveryService deliveryService, WebhookKeyService keyService, MailChainService chainService) {
        this.deliveryService = deliveryService;
        this.keyService = keyService;
        this.chainService = chainService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/public/webhooks/{key}/mail/brevo", this::brevoEvent);
        routes.post(prefix + "/public/webhooks/{key}/mail/sendgrid", this::sendGridEvents);
        routes.post(prefix + "/public/webhooks/{key}/mail/sweego", this::sweegoEvent);
    }

    /**
     * Sweego signs every call, so where a secret is configured the report is trusted because it is
     * provably Sweego's rather than because the caller knew the address. Without a secret the key
     * in the address is what there is, as with the other relays.
     */
    private void sweegoEvent(Context ctx) {
        var scope = authorise(ctx);
        String secret = chainService.sweegoSecret(scope.stationId());
        if (secret != null
                && !secret.isBlank()
                && !SweegoSignature.matches(
                        ctx.header("webhook-id"),
                        ctx.header("webhook-timestamp"),
                        ctx.header("webhook-signature"),
                        ctx.body(),
                        secret)) {
            log.warn("Sweego report refused: the signature does not match the body");
            throw new NotFoundResponse();
        }

        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        for (JsonNode entry : body.isArray() ? body : List.of(body)) {
            var status = sweegoStatus(text(entry, "event_type"));
            if (status == null) continue;
            deliveryService.record(
                    new MailDeliveryService.DeliveryEvent(
                            status,
                            text(entry, "recipient"),
                            null,
                            text(entry.path("headers"), "x-custom-header"),
                            text(entry, "swg_uid"),
                            text(entry, "details")),
                    scope.stationId());
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * What one of Sweego's event names means for delivery.
     *
     * <p>Sweego writes them inconsistently in its own documentation - {@code soft-bounce} with a
     * hyphen next to {@code hard_bounce} with an underscore - so both separators are ignored.
     * {@code email_sent} means Sweego took the message, which is what our own send already told us,
     * and says nothing about arrival.
     */
    private static MailDeliveryStatus sweegoStatus(String event) {
        if (event == null) return null;
        return switch (event.toLowerCase().replace("-", "").replace("_", "")) {
            case "delivered" -> MailDeliveryStatus.DELIVERED;
            case "softbounce" -> MailDeliveryStatus.SOFT_BOUNCE;
            case "hardbounce" -> MailDeliveryStatus.HARD_BOUNCE;
            case "complaint" -> MailDeliveryStatus.SPAM;
            default -> null;
        };
    }

    /**
     * SendGrid posts a batch of events at once rather than one at a time, and returns whatever was
     * put into the {@code unique_args} of the send as a field of its own.
     */
    private void sendGridEvents(Context ctx) {
        var scope = authorise(ctx);
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        if (!body.isArray()) {
            ctx.status(HttpStatus.NO_CONTENT);
            return;
        }
        for (JsonNode entry : body) {
            var status = sendGridStatus(text(entry, "event"));
            if (status == null) continue;
            deliveryService.record(
                    new MailDeliveryService.DeliveryEvent(
                            status,
                            text(entry, "email"),
                            null,
                            text(entry, "ember_id"),
                            text(entry, "sg_message_id"),
                            text(entry, "reason")),
                    scope.stationId());
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Works out who the caller speaks for, refusing anything the key does not authorise.
     */
    private WebhookKeyService.WebhookScope authorise(Context ctx) {
        return keyService.resolve(ctx.pathParam("key")).orElseThrow(() -> {
            log.warn("Delivery event refused: the key presented authorises nothing");
            return new NotFoundResponse();
        });
    }

    private void brevoEvent(Context ctx) {
        var scope = authorise(ctx);

        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        var status = brevoStatus(text(body, "event"));
        if (status == null) {
            // Opens, clicks and unsubscribes say nothing about delivery. Accept them so the
            // provider does not keep retrying, and do nothing else.
            ctx.status(HttpStatus.NO_CONTENT);
            return;
        }

        var event = new MailDeliveryService.DeliveryEvent(
                status,
                text(body, "email"),
                text(body, "subject"),
                text(body, "X-Mailin-custom"),
                text(body, "message-id"),
                text(body, "reason"));
        boolean matched = deliveryService.record(event, scope.stationId());
        ctx.status(matched ? HttpStatus.NO_CONTENT : HttpStatus.ACCEPTED);
    }

    /**
     * What one of Brevo's event names means for delivery.
     *
     * <p>Brevo writes them in camel case ({@code softBounce}, {@code hardBounce}), so the names are
     * compared without case. Anything about how a reader behaved - opening, clicking,
     * unsubscribing - is not a delivery outcome and maps to null.
     */
    private static MailDeliveryStatus brevoStatus(String event) {
        if (event == null) return null;
        return switch (event.toLowerCase().replace("_", "")) {
            case "delivered" -> MailDeliveryStatus.DELIVERED;
            case "softbounce" -> MailDeliveryStatus.SOFT_BOUNCE;
            case "hardbounce", "invalid", "invalidemail" -> MailDeliveryStatus.HARD_BOUNCE;
            case "blocked" -> MailDeliveryStatus.BLOCKED;
            case "spam", "complaint" -> MailDeliveryStatus.SPAM;
            case "deferred" -> MailDeliveryStatus.DEFERRED;
            case "error" -> MailDeliveryStatus.ERROR;
            default -> null;
        };
    }

    /**
     * What one of SendGrid's event names means for delivery.
     *
     * <p>SendGrid draws the line differently from Brevo: its {@code bounce} is the permanent one and
     * its {@code blocked} is the temporary refusal, while {@code dropped} means SendGrid itself
     * refused to send at all.
     */
    private static MailDeliveryStatus sendGridStatus(String event) {
        if (event == null) return null;
        return switch (event.toLowerCase()) {
            case "delivered" -> MailDeliveryStatus.DELIVERED;
            case "bounce" -> MailDeliveryStatus.HARD_BOUNCE;
            case "blocked" -> MailDeliveryStatus.BLOCKED;
            case "deferred" -> MailDeliveryStatus.DEFERRED;
            case "dropped" -> MailDeliveryStatus.ERROR;
            case "spamreport" -> MailDeliveryStatus.SPAM;
            default -> null;
        };
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isString() ? value.asString() : null;
    }
}
