/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.ConfigChanges;
import dev.chojo.ember.conf.file.elements.MailProviderEntry;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.entity.MailFallbackPayload;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.webhook.service.WebhookKeyService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * How the instance sends its own mail: the providers it goes out through, in order, and how often
 * notifications are gathered into one mail.
 *
 * <p>The provider list is written as a whole rather than entry by entry, because the order is the
 * point: a half-applied list would send mail through a route nobody asked for.
 */
@Singleton
public class InstanceMailSettingsService {
    private final Conf conf;
    private final ConfigChanges changes;
    private final WebhookKeyService webhookKeys;

    @Inject
    public InstanceMailSettingsService(Conf conf, ConfigChanges changes, WebhookKeyService webhookKeys) {
        this.conf = conf;
        this.changes = changes;
        this.webhookKeys = webhookKeys;
    }

    public MailingConfigResponse mailing() {
        var mailing = mailingConfig();
        return new MailingConfigResponse(mailing.notificationDigestIntervalMinutes(), mailing.stationShare());
    }

    /**
     * Writes the instance-wide mail settings. A request without the stations' share leaves it as it
     * is, so a client that does not know the setting cannot reset it.
     */
    public MailingConfigResponse updateMailing(MailingConfigRequest request) {
        var mailing = mailingConfig();
        Integer requestedShare = request.stationShare();
        int share = requestedShare == null ? mailing.stationShare() : requestedShare;
        if (share < 0 || share > 100) throw SystemRefusal.INSTANCE_MAIL_SHARE_OUT_OF_RANGE.raise();
        int intervalBefore = mailing.notificationDigestIntervalMinutes();
        int shareBefore = mailing.stationShare();
        changes.apply(
                () -> {
                    mailing.notificationDigestIntervalMinutes(request.notificationDigestIntervalMinutes());
                    mailing.stationShare(share);
                },
                () -> {
                    mailing.notificationDigestIntervalMinutes(intervalBefore);
                    mailing.stationShare(shareBefore);
                });
        return mailing();
    }

    /**
     * The providers the instance sends through, with their secrets masked and each carrying the
     * address it reports delivery events to.
     */
    public MailFallbackChain providers() {
        var mailing = mailingConfig();
        return new MailFallbackChain(
                Math.max(1, mailing.attempts()),
                mailing.providers().stream().map(this::payloadOf).toList());
    }

    /**
     * Replaces the providers the instance sends through. A secret sent back masked keeps the one
     * stored at the same position.
     *
     * <p>Emptying the list is what {@link #clear()} is for. A save that arrives empty is far more
     * often a client that failed to load it than an operator meaning to stop sending, and the
     * difference is not recoverable: the fields the first provider used to live in go with it.
     */
    public MailFallbackChain updateProviders(MailFallbackChain request) {
        var mailing = mailingConfig();
        var stored = mailing.providers();
        var entries = request.fallbacks() == null ? List.<MailFallbackPayload>of() : request.fallbacks();
        List<MailProviderEntry> next = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.provider() == null || entry.provider() == MailProviderType.NONE) continue;
            next.add(entryOf(entry, i < stored.size() ? stored.get(i) : null));
        }
        if (next.isEmpty() && !stored.isEmpty()) {
            throw SystemRefusal.INSTANCE_MAIL_PROVIDER_LIST_EMPTY.raise();
        }
        var before = mailing.senders();
        var after = before.withProviders(next);
        changes.apply(() -> mailing.senders(after), () -> mailing.senders(before));
        return providers();
    }

    /**
     * Empties the provider list, which is what stopping to send means now that the providers are
     * one list. The fields the first provider used to live in are cleared with it, so an instance
     * that has never been saved since does not fall back to them.
     */
    public void clear() {
        var mailing = mailingConfig();
        var before = mailing.senders();
        changes.apply(() -> mailing.senders(Mailing.Senders.NONE), () -> mailing.senders(before));
    }

    /**
     * Replaces the instance webhook key, which takes the old address out of service at once.
     *
     * @return the new address a provider reports delivery events to
     */
    public WebhookUrlResponse regenerateWebhookKey() {
        webhookKeys.regenerate(null);
        return new WebhookUrlResponse(webhookKeys.webhookUrl(conf.main().api().baseUrl(), null, "mail/brevo"));
    }

    private static MailProviderEntry entryOf(MailFallbackPayload entry, @Nullable MailProviderEntry previous) {
        return new MailProviderEntry(
                entry.provider(),
                entry.smtpHost(),
                entry.smtpPort(),
                entry.smtpEncryption(),
                entry.smtpUser(),
                MailFallbackPayload.keepOrReplace(entry.smtpPassword(), previous == null ? "" : previous.password()),
                MailFallbackPayload.keepOrReplace(entry.apiKey(), previous == null ? "" : previous.apiKey()),
                entry.senderAddress(),
                entry.senderName(),
                Math.max(1, entry.attempts()),
                Math.max(0, entry.dailySendLimit()));
    }

    private MailFallbackPayload payloadOf(MailProviderEntry entry) {
        return new MailFallbackPayload(
                        entry.provider(),
                        entry.host(),
                        entry.port(),
                        entry.encryption(),
                        entry.user(),
                        entry.password(),
                        entry.apiKey(),
                        entry.senderAddress(),
                        entry.senderName(),
                        entry.attempts(),
                        entry.dailySendLimit(),
                        "",
                        "",
                        "")
                .masked()
                .withWebhookUrl(webhookKeys.webhookUrl(
                        conf.main().api().baseUrl(), null, entry.provider().webhookPath()));
    }

    private Mailing mailingConfig() {
        return conf.main().mailing();
    }

    /**
     * @param attempts  how many attempts the first provider gets before the chain moves on
     * @param fallbacks the providers, in the order they are tried
     */
    public record MailFallbackChain(int attempts, List<MailFallbackPayload> fallbacks) {}

    /**
     * What is left of the mailing page once the providers became a list of their own: the settings
     * that belong to the instance rather than to any one provider.
     *
     * @param stationShare the percentage of each provider's daily limit that stations granted the
     *                     instance's providers may use together
     */
    public record MailingConfigResponse(int notificationDigestIntervalMinutes, int stationShare) {}

    /**
     * The fields the client may set. Read-only ones the response carries, the webhook address
     * among them, are accepted and dropped rather than refused, so a client holding an older
     * response does not fail on them.
     *
     * @param stationShare the percentage of each provider's daily limit that stations may use
     *                     together, or null to leave it as it is
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MailingConfigRequest(
            int notificationDigestIntervalMinutes, @Nullable Integer stationShare) {}

    /**
     * @param deliveryWebhookUrl the freshly minted address. It carries the instance webhook key, so
     *                           it is a secret in itself and is only ever handed to an administrator.
     */
    public record WebhookUrlResponse(String deliveryWebhookUrl) {}
}
