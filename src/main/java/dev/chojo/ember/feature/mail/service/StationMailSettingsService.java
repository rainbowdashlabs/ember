/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.entity.MailFallbackPayload;
import dev.chojo.ember.feature.mail.repository.ProviderSecretRepository;
import dev.chojo.ember.feature.mail.repository.StationMailProviderRepository;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.webhook.service.WebhookKeyService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How a station sends its own mail: the providers it goes out through, in order, and the address
 * those providers report back to.
 *
 * <p>A station's list is its own and never runs into the instance's: a station that has taken its
 * outgoing mail into its own hands keeps it there, rather than having its post leave under a
 * sender it did not choose. It gets a webhook address of its own for the same reason, so what it
 * hands to its provider can only ever touch its own post.
 */
@Singleton
public class StationMailSettingsService {
    private static final Logger log = LoggerFactory.getLogger(StationMailSettingsService.class);

    private final StationMailProviderRepository providers;
    private final ProviderSecretRepository secrets;
    private final WebhookKeyService webhookKeys;
    private final Api api;

    @Inject
    public StationMailSettingsService(
            StationMailProviderRepository providers,
            ProviderSecretRepository secrets,
            WebhookKeyService webhookKeys,
            Api api) {
        this.providers = providers;
        this.secrets = secrets;
        this.webhookKeys = webhookKeys;
        this.api = api;
    }

    private MailProviderType firstProvider(int stationId) {
        return firstEntry(stationId).map(MailChainEntry::provider).orElse(MailProviderType.NONE);
    }

    /**
     * The provider the station's mail goes out through first, or empty where it sends none of its own.
     */
    public Optional<MailChainEntry> firstEntry(int stationId) {
        return providers.findByStation(stationId).stream().findFirst();
    }

    /**
     * The address the station's first provider reports delivery events to.
     */
    public WebhookUrl webhook(int stationId) {
        var provider = firstProvider(stationId);
        return new WebhookUrl(
                webhookKeys.webhookUrl(api.baseUrl(), stationId, provider.webhookPath()),
                secrets.find(stationId, provider).isPresent());
    }

    /**
     * Stores the signing secret the first provider issued, so its reports are checked against it
     * rather than trusted for knowing the address. An empty value switches the check back off.
     */
    public WebhookUrl updateSigningSecret(int stationId, String secret) {
        var provider = firstProvider(stationId);
        secrets.store(stationId, provider, secret);
        log.info("Station {} set the signing secret of {}", stationId, provider);
        return webhook(stationId);
    }

    /**
     * Replaces the station's webhook key, which takes its old address out of service at once.
     */
    public WebhookUrl regenerateWebhook(int stationId) {
        webhookKeys.regenerate(stationId);
        log.info("Station {} replaced its webhook key", stationId);
        return webhook(stationId);
    }

    /**
     * The providers the station sends through, with their secrets masked and each carrying the
     * address it reports to.
     */
    public List<MailFallbackPayload> providers(int stationId) {
        return providers.findByStation(stationId).stream()
                .map(entry -> new MailFallbackPayload(
                                entry.provider(),
                                entry.smtpHost(),
                                entry.smtpPort(),
                                entry.smtpEncryption(),
                                entry.smtpUser(),
                                entry.smtpPassword(),
                                entry.apiKey(),
                                entry.senderAddress(),
                                entry.senderName(),
                                entry.attempts(),
                                entry.dailySendLimit(),
                                entry.providerName(),
                                entry.providerUrl(),
                                "")
                        .masked()
                        .withWebhookUrl(webhookKeys.webhookUrl(
                                api.baseUrl(), stationId, entry.provider().webhookPath())))
                .toList();
    }

    /**
     * Replaces the order the station falls back through. A secret sent back masked keeps the one
     * stored at the same position.
     *
     * <p>Emptying the list is what {@link #clear(int)} is for. A save that arrives empty is far more
     * often a client that failed to load it than a station meaning to stop sending.
     */
    public List<MailFallbackPayload> updateProviders(int stationId, List<MailFallbackPayload> incoming) {
        var stored = providers.findByStation(stationId);
        List<MailChainEntry> next = new ArrayList<>();
        for (int i = 0; i < incoming.size(); i++) {
            var entry = incoming.get(i);
            if (entry.provider() == null || entry.provider() == MailProviderType.NONE) continue;
            next.add(entryOf(next.size() + 1, entry, i < stored.size() ? stored.get(i) : null));
        }
        if (next.isEmpty() && !stored.isEmpty()) {
            throw StationRefusal.MAIL_PROVIDER_LIST_EMPTY.raise();
        }
        providers.replace(stationId, next);
        log.info("Station {} set {} mail fallback(s)", stationId, next.size());
        return providers(stationId);
    }

    private static MailChainEntry entryOf(int position, MailFallbackPayload entry, @Nullable MailChainEntry previous) {
        return new MailChainEntry(
                position,
                entry.provider(),
                entry.smtpHost(),
                entry.smtpPort(),
                entry.smtpEncryption(),
                entry.smtpUser(),
                MailFallbackPayload.keepOrReplace(
                        entry.smtpPassword(), previous == null ? "" : previous.smtpPassword()),
                MailFallbackPayload.keepOrReplace(entry.apiKey(), previous == null ? "" : previous.apiKey()),
                entry.senderAddress(),
                entry.senderName(),
                Math.max(1, entry.attempts()),
                Math.max(0, entry.dailySendLimit()),
                entry.providerName(),
                entry.providerUrl());
    }

    /**
     * Empties the station's list, which is what stopping to send its own mail means.
     */
    public void clear(int stationId) {
        providers.replace(stationId, List.of());
    }

    /**
     * Refuses unless the station has a provider of its own to send through.
     */
    public void requireProvider(int stationId) {
        if (providers.findByStation(stationId).isEmpty()) {
            throw StationRefusal.NO_MAIL_PROVIDER_SET.raise();
        }
    }

    /**
     * @param deliveryWebhookUrl the address to paste into the provider's settings
     * @param signingSecretSet   whether a signing secret is stored, without revealing it
     */
    public record WebhookUrl(String deliveryWebhookUrl, boolean signingSecretSet) {}
}
