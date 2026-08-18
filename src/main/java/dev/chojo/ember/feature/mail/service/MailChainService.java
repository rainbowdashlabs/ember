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
 * <p>The first entry is simply the first, not a provider of a different kind. One list per owner,
 * worked from the top: the instance's for system mail, the station's for station mail. A station's
 * list never runs into the instance's, so a station that has taken its outgoing mail into its own
 * hands keeps it there and its post does not silently leave under the instance's sender.
 */
@Singleton
public class MailChainService {

    private final Mailing mailing;
    private final StationMailProviderRepository providerRepository;
    private final ProviderSecretRepository secretRepository;

    @Inject
    public MailChainService(
            Mailing mailing,
            StationMailProviderRepository providerRepository,
            ProviderSecretRepository secretRepository) {
        this.mailing = mailing;
        this.providerRepository = providerRepository;
        this.secretRepository = secretRepository;
    }

    /**
     * The order system mail is tried through.
     */
    public List<MailChainEntry> forInstance() {
        List<MailChainEntry> chain = new ArrayList<>();
        int position = 0;
        for (var entry : mailing.providers()) {
            chain.add(new MailChainEntry(
                    position++,
                    entry.provider(),
                    entry.host(),
                    entry.port(),
                    entry.ssl(),
                    entry.user(),
                    entry.password(),
                    entry.apiKey(),
                    entry.senderAddress(),
                    entry.senderName(),
                    Math.max(1, entry.attempts()),
                    entry.dailySendLimit(),
                    "",
                    ""));
        }
        return configured(chain);
    }

    /**
     * The order a station's mail is tried through. Empty when the station sends through the
     * instance rather than through anything of its own.
     */
    public List<MailChainEntry> forStation(int stationId) {
        return configured(new ArrayList<>(providerRepository.findByStation(stationId)));
    }

    /**
     * The provider a station shows its members as the one carrying its post, which is the first it
     * sends through.
     */
    public Optional<MailChainEntry> firstForStation(int stationId) {
        return forStation(stationId).stream().findFirst();
    }

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
                    entry.attempts(),
                    entry.dailySendLimit(),
                    entry.providerName(),
                    entry.providerUrl()));
        }
        return usable;
    }
}
