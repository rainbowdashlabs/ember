/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.feature.mail.entity.EmailQueueStatus;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.entity.MailDeliveryStatus;
import dev.chojo.ember.feature.mail.entity.StationPoolUse;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.repository.InstanceMailGrantRepository;
import dev.chojo.ember.feature.mail.repository.MailProviderBlockRepository;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final MailProviderBlockRepository blockRepository;
    private final MailAllowance allowance;
    private final InstanceMailGrantRepository grantRepository;

    @Inject
    public MailDashboardService(
            EmailQueueRepository queueRepository,
            MailChainService chainService,
            MailProviderBlockRepository blockRepository,
            MailAllowance allowance,
            InstanceMailGrantRepository grantRepository) {
        this.queueRepository = queueRepository;
        this.chainService = chainService;
        this.blockRepository = blockRepository;
        this.allowance = allowance;
        this.grantRepository = grantRepository;
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
     * @param viaInstance    whether this is one of the instance's providers at the end of a
     *                       station's list, whose sent count is then the station's own mail only
     * @param pool           how the stations' share of it stands, for every provider of the
     *                       instance's list and for the instance's providers in a station's list;
     *                       null for a station's own provider
     */
    public record ProviderStanding(
            int position,
            MailProviderType provider,
            String senderAddress,
            int attempts,
            int dailySendLimit,
            int sentToday,
            int waiting,
            boolean exhausted,
            boolean viaInstance,
            @Nullable PoolStanding pool) {}

    /**
     * How the stations' share of one of the instance's providers stands today.
     *
     * @param limit     what all stations together may send through it today, or null where the
     *                  provider has no daily limit to take a share of and only each station's own
     *                  limit applies
     * @param sentToday what all stations together sent through it today
     * @param stations  which stations sent through it today and how much; on the instance's
     *                  overview only, since a station has no business knowing about the others
     */
    public record PoolStanding(@Nullable Integer limit, int sentToday, List<StationPoolUse> stations) {}

    /**
     * How the instance lends its providers to stations, on the instance's overview.
     *
     * @param sharePercent    the percentage of each provider's daily limit that stations may use
     *                        together
     * @param grantedStations how many stations may send through the instance's providers
     */
    public record PoolOverview(int sharePercent, int grantedStations) {}

    /**
     * One mail as the overview shows it.
     *
     * @param reachable whether anything in the list could still carry this one. False on a waiting
     *                  mail means it is not merely queued but stuck: every provider is either
     *                  refused by the receiving domain or out of allowance.
     */
    public record MailRecord(
            int id,
            String recipient,
            String subject,
            Instant createdAt,
            @Nullable Instant sentAt,
            EmailQueueStatus status,
            MailDeliveryStatus deliveryStatus,
            @Nullable String deliveryDetail,
            int attempts,
            int providerPosition,
            boolean reachable) {}

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
     * @param stuckMails      the left-behind mails themselves, oldest first, so they can be named
     *                        and put back rather than only counted
     * @param recent          the most recent mails, newest first
     * @param blocks          which provider a receiving domain refuses outright
     * @param pool            how the instance lends its providers to stations, on the instance's
     *                        overview only
     */
    public record MailDashboard(
            int pending,
            int sending,
            int sent,
            int failed,
            int stuck,
            @Nullable Instant oldestPendingAt,
            List<ProviderStanding> providers,
            List<MailRecord> stuckMails,
            List<MailRecord> recent,
            List<MailProviderBlockRepository.ProviderBlock> blocks,
            @Nullable PoolOverview pool) {}

    /**
     * Whether any provider could still carry a mail to this address, judged from the standings
     * already gathered rather than by asking the database again for every line.
     */
    private static boolean reachable(
            List<ProviderStanding> standings, Map<String, Set<MailProviderType>> refusedBy, String recipient) {
        String domain = MailProviderBlockRepository.domainOf(recipient);
        var refused = refusedBy.getOrDefault(domain, Set.of());
        return standings.stream().anyMatch(standing -> !standing.exhausted() && !refused.contains(standing.provider()));
    }

    /**
     * Gathers the overview.
     *
     * @param stationId the station whose post is meant, or null for the instance's
     */
    public MailDashboard forOwner(@Nullable Integer stationId) {
        var summary = queueRepository.summary(stationId);
        var chain = stationId == null ? chainService.forInstance() : chainService.forStation(stationId);
        var waiting = queueRepository.pendingByProvider(stationId);
        var stationUse = stationId == null ? stationUseByProvider() : Map.<Integer, List<StationPoolUse>>of();

        List<ProviderStanding> standings = new ArrayList<>();
        for (var entry : chain) {
            standings.add(new ProviderStanding(
                    entry.position(),
                    entry.provider(),
                    entry.senderAddress(),
                    entry.attempts(),
                    entry.dailySendLimit(),
                    allowance.sentToday(stationId, entry),
                    waiting.getOrDefault(entry.position(), 0),
                    !allowance.hasRoomToday(stationId, entry),
                    stationId != null && entry.isInstanceProvider(),
                    poolOf(entry, stationUse)));
        }

        var blocks = blockRepository.list(stationId);
        Map<String, Set<MailProviderType>> refusedBy = new HashMap<>();
        for (var block : blocks) {
            refusedBy
                    .computeIfAbsent(block.recipientDomain(), key -> new HashSet<>())
                    .add(block.provider());
        }

        List<MailRecord> recent = records(queueRepository.recent(stationId, RECENT_LIMIT), standings, refusedBy);
        List<MailRecord> stuckMails = records(queueRepository.stuck(stationId, RECENT_LIMIT), standings, refusedBy);

        return new MailDashboard(
                summary.pending(),
                summary.sending(),
                summary.sent(),
                summary.failed(),
                summary.stuck(),
                summary.oldestPendingAt(),
                standings,
                stuckMails,
                recent,
                blocks,
                stationId == null ? new PoolOverview(allowance.sharePercent(), grantRepository.countGranted()) : null);
    }

    /**
     * How the stations' share of a provider stands, or null for a station's own provider.
     *
     * @param stationUse what each station sent through each of the instance's providers today, empty
     *                   on a station's overview
     */
    private @Nullable PoolStanding poolOf(MailChainEntry entry, Map<Integer, List<StationPoolUse>> stationUse) {
        Integer instancePosition = entry.instancePosition();
        if (instancePosition == null) return null;
        var limit = allowance.stationPool(entry.dailySendLimit());
        return new PoolStanding(
                limit.isPresent() ? limit.getAsInt() : null,
                allowance.stationShareSentToday(instancePosition),
                stationUse.getOrDefault(instancePosition, List.of()));
    }

    private Map<Integer, List<StationPoolUse>> stationUseByProvider() {
        Map<Integer, List<StationPoolUse>> byProvider = new HashMap<>();
        for (var use : queueRepository.stationPoolUse(LocalDate.now())) {
            byProvider
                    .computeIfAbsent(use.instancePosition(), key -> new ArrayList<>())
                    .add(use);
        }
        return byProvider;
    }

    private static List<MailRecord> records(
            List<EmailQueueRepository.QueueEntry> entries,
            List<ProviderStanding> standings,
            Map<String, Set<MailProviderType>> refusedBy) {
        return entries.stream()
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
                        entry.providerPosition(),
                        reachable(standings, refusedBy, entry.recipient())))
                .toList();
    }

    /**
     * Puts mails a dead worker left in sending back into the queue.
     *
     * <p>A mail can only be left behind, never taken from a worker still at it, so the worst this
     * can do is send one twice: the process may have died after the provider had already accepted
     * it. That is the trade an operator makes knowingly, which is why nothing does it by itself.
     *
     * @param stationId the station whose post is meant, or null for the instance's
     * @param id        the one mail meant, or null for every stuck one
     */
    public RequeuedMails requeueStuck(@Nullable Integer stationId, @Nullable Integer id) {
        return new RequeuedMails(queueRepository.requeueStuck(stationId, id));
    }

    /**
     * Lifts a block by hand, for when an operator knows the relay has been taken off the list and
     * does not want to wait out the week.
     *
     * @param stationId the station whose post is meant, or null for the instance's
     * @param provider  the provider the block was put on
     * @param domain    the recipient domain it was put on, or null when none was named, which lifts nothing
     */
    public void liftBlock(@Nullable Integer stationId, MailProviderType provider, @Nullable String domain) {
        if (domain == null) return;
        blockRepository.lift(stationId, provider, domain);
    }

    /**
     * @param requeued how many left-behind mails went back into the queue
     */
    public record RequeuedMails(int requeued) {}
}
