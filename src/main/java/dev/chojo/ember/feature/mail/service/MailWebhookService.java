/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.mail.entity.MailDeliveryStatus;
import dev.chojo.ember.feature.webhook.service.WebhookKeyService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

/**
 * What a mail provider reports about the messages it took from us, turned into delivery outcomes.
 *
 * <p>What stands in the way of a stranger is the webhook key in the address, which also decides what
 * the caller may touch: the instance key answers for all mail, a station key only for that station's.
 * Every refusal answers the same code as a wrong key, so somebody who guessed the address learns
 * nothing about how close they came; the log names the actual reason.
 */
@Singleton
public class MailWebhookService {
    private static final Logger log = LoggerFactory.getLogger(MailWebhookService.class);

    private final MailDeliveryService deliveryService;
    private final WebhookKeyService keyService;
    private final MailChainService chainService;
    private final Clock clock;

    @Inject
    public MailWebhookService(
            MailDeliveryService deliveryService, WebhookKeyService keyService, MailChainService chainService) {
        this(deliveryService, keyService, chainService, Clock.systemUTC());
    }

    /**
     * The same, judging how old a signed call is by the given clock.
     *
     * @param clock what now is
     */
    MailWebhookService(
            MailDeliveryService deliveryService,
            WebhookKeyService keyService,
            MailChainService chainService,
            Clock clock) {
        this.deliveryService = deliveryService;
        this.keyService = keyService;
        this.chainService = chainService;
        this.clock = clock;
    }

    /**
     * Works out who the caller speaks for, refusing anything the key does not authorise.
     */
    private WebhookKeyService.WebhookScope authorise(String key) {
        return keyService.resolve(key).orElseThrow(() -> {
            log.warn("Delivery event refused: the key presented authorises nothing");
            return Refusal.MAIL_REPORT_NOT_TAKEN.raise();
        });
    }

    /**
     * Takes a Sweego report, one event or a list of them.
     *
     * <p>Sweego signs every call, so where a secret is configured the report is trusted because it
     * is provably Sweego's rather than because the caller knew the address: an unsigned call, a stale
     * timestamp and a signature this secret did not produce are all refused. Without a secret the
     * key in the address is what there is, as with the other relays.
     *
     * @param key  the webhook key from the address
     * @param call the call as it arrived, signature headers and body
     */
    public void sweego(String key, SignedCall call) {
        var scope = authorise(key);
        String secret = chainService.sweegoSecret(scope.stationId());
        if (!secret.isBlank()) {
            var verdict = SweegoSignature.verify(
                    call.id(), call.timestamp(), call.signature(), call.rawBody(), secret, clock.instant());
            if (verdict != SweegoSignature.Verdict.VALID) {
                log.warn("Sweego report refused: {}", verdict);
                throw Refusal.MAIL_REPORT_NOT_TAKEN.raise();
            }
        }
        var body = call.body();
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
    }

    /**
     * Takes a SendGrid report. SendGrid posts a batch of events at once rather than one at a time,
     * and returns whatever was put into the {@code unique_args} of the send as a field of its own.
     */
    public void sendGrid(String key, JsonNode body) {
        var scope = authorise(key);
        if (!body.isArray()) return;
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
    }

    /**
     * Takes a Brevo report. Opens, clicks and unsubscribes say nothing about delivery: they are
     * accepted so the provider does not keep retrying, and nothing else is done with them.
     *
     * @return whether the report was settled: said nothing about delivery, or matched a mail we sent
     */
    public boolean brevo(String key, JsonNode body) {
        var scope = authorise(key);
        var status = brevoStatus(text(body, "event"));
        if (status == null) return true;
        return deliveryService.record(
                new MailDeliveryService.DeliveryEvent(
                        status,
                        text(body, "email"),
                        text(body, "subject"),
                        text(body, "X-Mailin-custom"),
                        text(body, "message-id"),
                        text(body, "reason")),
                scope.stationId());
    }

    /**
     * What one of Sweego's event names means for delivery.
     *
     * <p>Sweego writes them inconsistently in its own documentation, {@code soft-bounce} with a
     * hyphen next to {@code hard_bounce} with an underscore, so both separators are ignored.
     * {@code email_sent} means Sweego took the message, which is what our own send already told us,
     * and says nothing about arrival.
     */
    private static @Nullable MailDeliveryStatus sweegoStatus(@Nullable String event) {
        if (event == null) return null;
        return switch (event.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "")) {
            case "delivered" -> MailDeliveryStatus.DELIVERED;
            case "softbounce" -> MailDeliveryStatus.SOFT_BOUNCE;
            case "hardbounce" -> MailDeliveryStatus.HARD_BOUNCE;
            case "complaint" -> MailDeliveryStatus.SPAM;
            default -> null;
        };
    }

    /**
     * What one of Brevo's event names means for delivery.
     *
     * <p>Brevo writes them in camel case ({@code softBounce}, {@code hardBounce}), so the names are
     * compared without case. Anything about how a reader behaved, opening, clicking, unsubscribing,
     * is not a delivery outcome and maps to null.
     */
    private static @Nullable MailDeliveryStatus brevoStatus(@Nullable String event) {
        if (event == null) return null;
        return switch (event.toLowerCase(Locale.ROOT).replace("_", "")) {
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
    private static @Nullable MailDeliveryStatus sendGridStatus(@Nullable String event) {
        if (event == null) return null;
        return switch (event.toLowerCase(Locale.ROOT)) {
            case "delivered" -> MailDeliveryStatus.DELIVERED;
            case "bounce" -> MailDeliveryStatus.HARD_BOUNCE;
            case "blocked" -> MailDeliveryStatus.BLOCKED;
            case "deferred" -> MailDeliveryStatus.DEFERRED;
            case "dropped" -> MailDeliveryStatus.ERROR;
            case "spamreport" -> MailDeliveryStatus.SPAM;
            default -> null;
        };
    }

    private static @Nullable String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isString() ? value.asString() : null;
    }

    /**
     * A signed call as it arrived.
     *
     * @param id        the {@code webhook-id} header, or null when it is missing
     * @param timestamp the {@code webhook-timestamp} header, or null when it is missing
     * @param signature the {@code webhook-signature} header, or null when it is missing
     * @param rawBody   the body exactly as sent, which is what was signed
     * @param body      the same body, read
     */
    public record SignedCall(
            @Nullable String id,
            @Nullable String timestamp,
            @Nullable String signature,
            String rawBody,
            JsonNode body) {}
}
