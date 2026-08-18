/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.route;

import dev.chojo.ember.feature.station.entity.MailProviderType;

/**
 * One provider in a fallback chain, as it travels to and from a client.
 *
 * <p>The same shape serves the instance chain and a station's, so the screen for editing one is the
 * screen for editing the other.
 *
 * <p>Secrets travel outwards masked and inwards optional: a client that sends the mask back means
 * "leave it as it was", which is how an administrator can reorder a chain without being shown, or
 * having to retype, every password in it.
 *
 * @param attempts how many attempts this provider gets before the next one takes over
 */
public record MailFallbackPayload(
        MailProviderType provider,
        String smtpHost,
        int smtpPort,
        boolean smtpSsl,
        String smtpUser,
        String smtpPassword,
        String apiKey,
        String senderAddress,
        String senderName,
        int attempts,
        int dailySendLimit,
        String providerName,
        String providerUrl,
        String deliveryWebhookUrl) {

    /**
     * What a stored secret looks like on its way out.
     */
    public static final String MASK = "********";

    /**
     * Whether a value coming back from a client is the mask rather than a new secret.
     */
    public static boolean isMask(String value) {
        return MASK.equals(value);
    }

    /**
     * The secret to store: the one just entered, or the one already held when the client sent the
     * mask back.
     */
    public static String keepOrReplace(String incoming, String stored) {
        if (incoming == null || isMask(incoming)) return stored == null ? "" : stored;
        return incoming;
    }

    /**
     * The same entry with its secrets masked, ready to be handed to a client.
     */
    public MailFallbackPayload masked() {
        return new MailFallbackPayload(
                provider,
                smtpHost,
                smtpPort,
                smtpSsl,
                smtpUser,
                smtpPassword == null || smtpPassword.isEmpty() ? "" : MASK,
                apiKey == null || apiKey.isEmpty() ? "" : MASK,
                senderAddress,
                senderName,
                attempts,
                dailySendLimit,
                providerName,
                providerUrl,
                deliveryWebhookUrl);
    }

    /**
     * The same entry carrying the address this provider reports delivery events to.
     *
     * <p>Every entry gets one of its own: the address ends in the report format the provider sends,
     * so a list holding two different providers needs two different addresses, and the one further
     * down is exactly the one nobody would think to ask for until it is carrying the post.
     */
    public MailFallbackPayload withWebhookUrl(String url) {
        return new MailFallbackPayload(
                provider,
                smtpHost,
                smtpPort,
                smtpSsl,
                smtpUser,
                smtpPassword,
                apiKey,
                senderAddress,
                senderName,
                attempts,
                dailySendLimit,
                providerName,
                providerUrl,
                url);
    }
}
