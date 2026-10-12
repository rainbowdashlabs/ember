/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.entity.StationMailSender;
import dev.chojo.ember.feature.mail.repository.InstanceMailGrantRepository;
import dev.chojo.ember.feature.mail.repository.ProviderSecretRepository;
import dev.chojo.ember.feature.mail.repository.StationMailProviderRepository;
import dev.chojo.ember.feature.mail.repository.StationMailSenderRepository;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

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
 * list runs into the instance's only where an instance administrator granted it that: its own
 * providers come first and the instance's follow as the last entries, so a station that has taken
 * its outgoing mail into its own hands keeps it there, and its post leaves under the instance's
 * sender only when its own providers are spent and the instance agreed to carry it.
 */
@Singleton
public class MailChainService {

    private final Mailing mailing;
    private final StationMailProviderRepository providerRepository;
    private final ProviderSecretRepository secretRepository;
    private final InstanceMailGrantRepository grantRepository;
    private final StationMailSenderRepository senderRepository;

    @Inject
    public MailChainService(
            Mailing mailing,
            StationMailProviderRepository providerRepository,
            ProviderSecretRepository secretRepository,
            InstanceMailGrantRepository grantRepository,
            StationMailSenderRepository senderRepository) {
        this.mailing = mailing;
        this.providerRepository = providerRepository;
        this.secretRepository = secretRepository;
        this.grantRepository = grantRepository;
        this.senderRepository = senderRepository;
    }

    /**
     * The order system mail is tried through. Every entry is marked as the instance's provider at
     * its place in the list.
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
                    entry.encryption(),
                    entry.user(),
                    entry.password(),
                    entry.apiKey(),
                    entry.senderAddress(),
                    entry.senderName(),
                    Math.max(1, entry.attempts()),
                    entry.dailySendLimit(),
                    entry.providerName(),
                    entry.providerUrl()));
        }
        return configured(chain).stream()
                .map(entry -> entry.asInstanceProvider(entry.position()))
                .toList();
    }

    /**
     * The order a station's mail is tried through: its own providers, followed by the instance's
     * where the station was granted them. Empty when it has neither.
     *
     * <p>The instance's providers send from the instance's address, because that is the one its
     * providers have authorised, but under the station's name: a reader should see who wrote to
     * them. Replies go to the station's reply address where it set one, through any provider.
     */
    public List<MailChainEntry> forStation(int stationId) {
        var sender = senderRepository.find(stationId).orElse(null);
        List<MailChainEntry> chain = new ArrayList<>(own(stationId, sender));
        if (sender == null || grantRepository.find(stationId).isEmpty()) return chain;
        for (var entry : forInstance()) {
            chain.add(entry.withPosition(chain.size()).sendingAs(sender.name(), sender.replyTo()));
        }
        return chain;
    }

    /**
     * The station's own providers, without anything the instance lends it. What the station
     * configures, tries and shows its members is this list.
     */
    public List<MailChainEntry> ownForStation(int stationId) {
        return own(stationId, senderRepository.find(stationId).orElse(null));
    }

    private List<MailChainEntry> own(int stationId, @Nullable StationMailSender sender) {
        var chain = configured(new ArrayList<>(providerRepository.findByStation(stationId)));
        if (sender == null || sender.replyTo().isBlank()) return chain;
        return chain.stream()
                .map(entry -> entry.sendingAs(entry.senderName(), sender.replyTo()))
                .toList();
    }

    /**
     * The provider a station shows its members as the one carrying its post, which is the first of
     * its own.
     */
    public Optional<MailChainEntry> firstForStation(int stationId) {
        return ownForStation(stationId).stream().findFirst();
    }

    /**
     * The signing secret Sweego issued for whoever owns this chain, or empty when reports from it
     * are not checked against one.
     *
     * @param stationId the station, or null for the instance
     */
    public String sweegoSecret(@Nullable Integer stationId) {
        if (stationId == null) return mailing.sweegoWebhookSecret();
        return secretRepository.find(stationId, MailProviderType.SWEEGO).orElse("");
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
            usable.add(entry.withPosition(usable.size()));
        }
        return usable;
    }
}
