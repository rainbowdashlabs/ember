/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.repository.ProviderSecretRepository;
import dev.chojo.ember.feature.mail.repository.StationMailProviderRepository;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.repository.StationMailConfigRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The order a mail is tried through.
 *
 * <p>Sending is not one provider but a list. Each entry gets a number of attempts; when it has used
 * them, the next takes over, and when the list is exhausted the mail has failed. This is what makes
 * a relay whose address has landed on somebody's block list survivable - the mail goes out by
 * another route instead of disappearing.
 *
 * <p>The first entry is the provider already configured today: the instance's own for system mail,
 * the station's own for station mail. Everything after it is a fallback. A station's chain never
 * runs into the instance's: a station that has taken its outgoing mail into its own hands keeps it
 * there, and its post does not silently leave under the instance's sender.
 *
 * <p>The seam is deliberate and worth naming: the first provider still lives where it always did -
 * in the configuration for the instance, in {@code station_mail_config} for a station - while the
 * fallbacks live in their own places. Normalising the first one into the same list would be
 * tidier, and would also change what a station transfer carries, so it is a separate piece of work.
 * Nothing outside this service sees the seam; everyone else is handed a list.
 */
@Singleton
public class MailChainService {

    private final Mailing mailing;
    private final StationMailConfigRepository configRepository;
    private final StationMailProviderRepository providerRepository;
    private final ProviderSecretRepository secretRepository;

    @Inject
    public MailChainService(
            Mailing mailing,
            StationMailConfigRepository configRepository,
            StationMailProviderRepository providerRepository,
            ProviderSecretRepository secretRepository) {
        this.mailing = mailing;
        this.configRepository = configRepository;
        this.providerRepository = providerRepository;
        this.secretRepository = secretRepository;
    }

    /**
     * The order system mail is tried through.
     */
    public List<MailChainEntry> forInstance() {
        List<MailChainEntry> chain = new ArrayList<>();
        var smtp = mailing.smtp();
        chain.add(new MailChainEntry(
                0,
                mailing.provider(),
                smtp.host(),
                smtp.port(),
                smtp.ssl(),
                mailing.user(),
                mailing.password(),
                mailing.apiKey(),
                mailing.senderAddress(),
                mailing.senderName(),
                Math.max(1, mailing.attempts())));
        int position = 1;
        for (var fallback : mailing.fallbacks()) {
            chain.add(new MailChainEntry(
                    position++,
                    fallback.provider(),
                    fallback.host(),
                    fallback.port(),
                    fallback.ssl(),
                    fallback.user(),
                    fallback.password(),
                    fallback.apiKey(),
                    fallback.senderAddress(),
                    fallback.senderName(),
                    Math.max(1, fallback.attempts())));
        }
        return configured(chain);
    }

    /**
     * The order a station's mail is tried through. Empty when the station sends through the
     * instance rather than through anything of its own.
     */
    public List<MailChainEntry> forStation(int stationId) {
        var config = configRepository.findByStation(stationId);
        if (config.isEmpty() || !config.get().isConfigured()) return List.of();
        var c = config.get();
        List<MailChainEntry> chain = new ArrayList<>();
        chain.add(new MailChainEntry(
                0,
                c.provider(),
                c.smtpHost(),
                c.smtpPort(),
                c.smtpSsl(),
                c.smtpUser(),
                c.smtpPassword(),
                c.apiKey(),
                c.senderAddress(),
                c.senderName(),
                DEFAULT_STATION_ATTEMPTS));
        chain.addAll(providerRepository.findByStation(stationId));
        return configured(chain);
    }

    /**
     * How many attempts a station's own provider gets before its first fallback takes over. Not
     * configurable yet - a station chooses its fallbacks, and two attempts is what the instance
     * defaults to as well.
     */
    private static final int DEFAULT_STATION_ATTEMPTS = 2;

    /**
     * The signing secret Sweego issued for whoever owns this chain, or null when reports from it
     * are not checked against one.
     *
     * @param stationId the station, or null for the instance
     */
    public String sweegoSecret(Integer stationId) {
        if (stationId == null) return mailing.sweegoWebhookSecret();
        return secretRepository.find(stationId, MailProviderType.SWEEGO).orElse(null);
    }

    /**
     * The entry currently in turn.
     *
     * @param chain    the order to walk
     * @param position how far the mail has got
     * @return the entry, or empty when the chain is exhausted
     */
    public Optional<MailChainEntry> at(List<MailChainEntry> chain, int position) {
        if (position < 0 || position >= chain.size()) return Optional.empty();
        return Optional.of(chain.get(position));
    }

    /**
     * Drops entries that name no provider, so a chain with a gap in the middle still reads as an
     * order rather than stopping at the gap. Positions are renumbered onto what is left.
     */
    private static List<MailChainEntry> configured(List<MailChainEntry> chain) {
        List<MailChainEntry> usable = new ArrayList<>();
        for (var entry : chain) {
            if (entry.provider() == null || entry.provider() == MailProviderType.NONE) continue;
            usable.add(new MailChainEntry(
                    usable.size(),
                    entry.provider(),
                    entry.smtpHost(),
                    entry.smtpPort(),
                    entry.smtpSsl(),
                    entry.smtpUser(),
                    entry.smtpPassword(),
                    entry.apiKey(),
                    entry.senderAddress(),
                    entry.senderName(),
                    entry.attempts()));
        }
        return usable;
    }
}
