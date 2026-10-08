/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.repository.InstanceMailGrantRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;

/**
 * How much a provider may still send today, for whoever's mail it is.
 *
 * <p>A station's own provider answers to its own daily limit alone. An instance provider answers to
 * its daily limit for everybody together, because that is what the provider sells. Stations granted
 * the instance's providers answer to two more limits on top: all stations together may use only
 * the share of each provider the instance set aside for them, so the rest stays free for the
 * instance's own mail, and a station may carry a daily limit of its own across all of them. The
 * instance's own mail is held to the provider's limit only, never to the share.
 *
 * <p>A provider without a daily limit has no share to take a part of, so there only the stations'
 * own limits apply.
 */
@Singleton
public class MailAllowance {

    private final Mailing mailing;
    private final EmailQueueRepository queueRepository;
    private final InstanceMailGrantRepository grantRepository;

    @Inject
    public MailAllowance(
            Mailing mailing, EmailQueueRepository queueRepository, InstanceMailGrantRepository grantRepository) {
        this.mailing = mailing;
        this.queueRepository = queueRepository;
        this.grantRepository = grantRepository;
    }

    /**
     * How many mails all stations together may send through a provider in a day.
     *
     * @param dailySendLimit the provider's daily limit, zero for none
     * @param sharePercent   the percentage set aside for stations
     * @return the stations' share, rounded down, or empty where the provider has no limit to take a
     *     share of
     */
    public static OptionalInt stationPool(int dailySendLimit, int sharePercent) {
        if (dailySendLimit <= 0) return OptionalInt.empty();
        return OptionalInt.of((int) ((long) dailySendLimit * Math.clamp(sharePercent, 0, 100) / 100));
    }

    /**
     * The stations' share of a provider under the configured percentage.
     *
     * @param dailySendLimit the provider's daily limit, zero for none
     */
    public OptionalInt stationPool(int dailySendLimit) {
        return stationPool(dailySendLimit, sharePercent());
    }

    /**
     * The percentage of each instance provider's daily limit that stations may use together.
     */
    public int sharePercent() {
        return mailing.stationShare();
    }

    /**
     * Whether any provider of the chain could still carry a mail today.
     *
     * @param stationId the station whose chain it is, or null for the instance's
     */
    public boolean anyRoomToday(@Nullable Integer stationId, List<MailChainEntry> chain) {
        return chain.stream().anyMatch(entry -> hasRoomToday(stationId, entry));
    }

    /**
     * Whether one provider of a chain could still carry a mail today.
     *
     * @param stationId the station whose chain it is, or null for the instance's
     * @param entry     the provider
     */
    public boolean hasRoomToday(@Nullable Integer stationId, MailChainEntry entry) {
        LocalDate today = LocalDate.now();
        Integer instancePosition = entry.instancePosition();
        if (instancePosition == null) {
            return stationId != null
                    && entry.hasRoomToday(queueRepository.ownProviderDailyCount(today, stationId, entry.position()));
        }
        if (!entry.hasRoomToday(queueRepository.instanceProviderDailyCount(today, instancePosition))) return false;
        return stationId == null || stationHasRoom(stationId, entry, instancePosition, today);
    }

    /**
     * What one provider of a chain has sent today on behalf of the chain's owner.
     *
     * @param stationId the station whose chain it is, or null for the instance's, which counts
     *                  everything the provider sent since its allowance is shared
     * @param entry     the provider
     */
    public int sentToday(@Nullable Integer stationId, MailChainEntry entry) {
        LocalDate today = LocalDate.now();
        Integer instancePosition = entry.instancePosition();
        if (instancePosition == null) {
            return stationId == null ? 0 : queueRepository.ownProviderDailyCount(today, stationId, entry.position());
        }
        if (stationId == null) return queueRepository.instanceProviderDailyCount(today, instancePosition);
        return queueRepository.stationViaInstanceDailyCount(today, stationId, instancePosition);
    }

    /**
     * What all stations together sent through one of the instance's providers today.
     *
     * @param instancePosition where the provider sits in the instance's list
     */
    public int stationShareSentToday(int instancePosition) {
        return queueRepository.stationShareDailyCount(LocalDate.now(), instancePosition);
    }

    /**
     * What one station sent through all of the instance's providers today, which is what its own
     * daily limit is measured against.
     */
    public int stationSentViaInstanceToday(int stationId) {
        return queueRepository.stationViaInstanceDailyCount(LocalDate.now(), stationId, null);
    }

    private boolean stationHasRoom(int stationId, MailChainEntry entry, int instancePosition, LocalDate today) {
        var grant = grantRepository.find(stationId);
        if (grant.isEmpty()) return false;
        if (!grant.get().hasRoomToday(queueRepository.stationViaInstanceDailyCount(today, stationId, null))) {
            return false;
        }
        var pool = stationPool(entry.dailySendLimit());
        return pool.isEmpty() || queueRepository.stationShareDailyCount(today, instancePosition) < pool.getAsInt();
    }
}
