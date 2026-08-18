/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.feature.mail.entity.MailDeliveryStatus;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * What has become of the post, gathered in one place.
 *
 * <p>Everything here was already recorded and none of it could be read: how many mails wait, which
 * provider they wait at, what a provider reported back about the ones it took. Sending mail without
 * this is flying by the instruments being switched off.
 */
@Singleton
public class MailDashboardService {

    /** How many recent mails the overview carries. Enough to see a pattern, not a mail archive. */
    private static final int RECENT_LIMIT = 50;

    private final EmailQueueRepository queueRepository;
    private final MailChainService chainService;

    @Inject
    public MailDashboardService(EmailQueueRepository queueRepository, MailChainService chainService) {
        this.queueRepository = queueRepository;
        this.chainService = chainService;
    }

    /**
     * How one provider of the list stands today.
     *
     * @param position       where in the order it sits
     * @param provider       which service it is
     * @param senderAddress  the address it sends under, so two entries of the same service are
     *                       told apart
     * @param attempts       how many attempts it gets before the next takes over
     * @param dailySendLimit what it may send in a day, or zero for no limit
     * @param sentToday      what it has sent today
     * @param waiting        how many mails sit at this provider right now, which is what says who
     *                       carries the next one
     * @param exhausted      whether its allowance is spent, so the next one is carrying the post
     */
    public record ProviderStanding(
            int position,
            MailProviderType provider,
            String senderAddress,
            int attempts,
            int dailySendLimit,
            int sentToday,
            int waiting,
            boolean exhausted) {}

    /**
     * One mail as the overview shows it.
     */
    public record MailRecord(
            int id,
            String recipient,
            String subject,
            Instant createdAt,
            Instant sentAt,
            String status,
            MailDeliveryStatus deliveryStatus,
            String deliveryDetail,
            int attempts,
            int providerPosition) {}

    /**
     * The whole picture for one owner.
     *
     * @param pending         mails waiting for the next round
     * @param sending         mails handed to the worker and not yet answered for
     * @param sent            mails a provider accepted
     * @param failed          mails every provider refused
     * @param stuck           mails left in sending by a worker that died, which nothing retries
     * @param oldestPendingAt when the longest-waiting mail was written, or null when none waits
     * @param providers       how each provider of the list stands today
     * @param recent          the most recent mails, newest first
     */
    public record MailDashboard(
            int pending,
            int sending,
            int sent,
            int failed,
            int stuck,
            Instant oldestPendingAt,
            List<ProviderStanding> providers,
            List<MailRecord> recent) {}

    /**
     * Gathers the overview.
     *
     * @param stationId the station whose post is meant, or null for the instance's
     */
    public MailDashboard forOwner(Integer stationId) {
        var summary = queueRepository.summary(stationId);
        var chain = stationId == null ? chainService.forInstance() : chainService.forStation(stationId);
        var waiting = queueRepository.pendingByProvider(stationId);
        LocalDate today = LocalDate.now();

        List<ProviderStanding> standings = new ArrayList<>();
        for (int position = 0; position < chain.size(); position++) {
            var entry = chain.get(position);
            int sentToday = queueRepository.getProviderDailyCount(today, stationId, position);
            standings.add(new ProviderStanding(
                    position,
                    entry.provider(),
                    entry.senderAddress(),
                    entry.attempts(),
                    entry.dailySendLimit(),
                    sentToday,
                    waiting.getOrDefault(position, 0),
                    !entry.hasRoomToday(sentToday)));
        }

        List<MailRecord> recent = queueRepository.recent(stationId, RECENT_LIMIT).stream()
                .map(entry -> new MailRecord(
                        entry.id(),
                        entry.recipient(),
                        entry.subject(),
                        entry.createdAt(),
                        entry.sentAt(),
                        entry.status(),
                        entry.deliveryStatus(),
                        entry.deliveryDetail(),
                        entry.attempts(),
                        entry.providerPosition()))
                .toList();

        return new MailDashboard(
                summary.pending(),
                summary.sending(),
                summary.sent(),
                summary.failed(),
                summary.stuck(),
                summary.oldestPendingAt(),
                standings,
                recent);
    }
}
