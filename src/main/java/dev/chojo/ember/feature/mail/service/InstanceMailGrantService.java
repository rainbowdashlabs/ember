/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.feature.mail.entity.InstanceMailStation;
import dev.chojo.ember.feature.mail.repository.InstanceMailGrantRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Which stations may send their own mail through the instance's providers, as the instance's
 * administrators grant and withdraw it, one station at a time or many at once.
 *
 * <p>A grant may carry a daily limit of the station's own. Across all granted stations, the share of
 * each provider the instance sets aside for stations holds as well, whatever the single limits add
 * up to.
 */
@Singleton
public class InstanceMailGrantService {
    private static final Logger log = LoggerFactory.getLogger(InstanceMailGrantService.class);

    private final InstanceMailGrantRepository grants;
    private final StationRepository stations;

    @Inject
    public InstanceMailGrantService(InstanceMailGrantRepository grants, StationRepository stations) {
        this.grants = grants;
        this.stations = stations;
    }

    /**
     * Every station, granted or not, with what the instance's providers sent for it today.
     */
    public List<InstanceMailStation> stations() {
        return grants.stations(LocalDate.now());
    }

    /**
     * One station, named by its identifier.
     */
    public InstanceMailStation station(UUID stationUid) {
        return forStation(stationIdOf(stationUid));
    }

    /**
     * One station, as it sees its own grant.
     */
    public InstanceMailStation forStation(int stationId) {
        return grants.station(stationId, LocalDate.now())
                .orElseThrow(SystemRefusal.STATION_NOT_HERE_FOR_INSTANCE_MAIL::raise);
    }

    /**
     * Lets the station send through the instance's providers, or changes its daily limit there.
     *
     * @param dailyLimit how many mails a day it may send through them, or null for no limit of its own
     */
    public InstanceMailStation grant(UUID stationUid, @Nullable Integer dailyLimit) {
        requireLimit(dailyLimit);
        int stationId = stationIdOf(stationUid);
        grants.grant(stationId, dailyLimit);
        log.info("Station {} may send through the instance's mail providers, daily limit {}", stationId, dailyLimit);
        return forStation(stationId);
    }

    /**
     * Takes the instance's providers away from the station.
     */
    public InstanceMailStation withdraw(UUID stationUid) {
        int stationId = stationIdOf(stationUid);
        grants.withdraw(stationId);
        log.info("Station {} no longer sends through the instance's mail providers", stationId);
        return forStation(stationId);
    }

    /**
     * Grants several stations at once, all with the same daily limit. Nothing is written unless
     * every station named exists.
     */
    public List<InstanceMailStation> grantAll(List<UUID> stationUids, @Nullable Integer dailyLimit) {
        requireLimit(dailyLimit);
        var ids = stationIdsOf(stationUids);
        ids.forEach(stationId -> grants.grant(stationId, dailyLimit));
        log.info("{} stations may send through the instance's mail providers, daily limit {}", ids.size(), dailyLimit);
        return stations();
    }

    /**
     * Withdraws several stations at once. Nothing is written unless every station named exists.
     */
    public List<InstanceMailStation> withdrawAll(List<UUID> stationUids) {
        var ids = stationIdsOf(stationUids);
        ids.forEach(grants::withdraw);
        log.info("{} stations no longer send through the instance's mail providers", ids.size());
        return stations();
    }

    private static void requireLimit(@Nullable Integer dailyLimit) {
        if (dailyLimit != null && dailyLimit < 1) throw SystemRefusal.INSTANCE_MAIL_LIMIT_NOT_POSITIVE.raise();
    }

    private List<Integer> stationIdsOf(List<UUID> stationUids) {
        return stationUids.stream().map(this::stationIdOf).toList();
    }

    private int stationIdOf(UUID stationUid) {
        return stations.resolveId(stationUid).orElseThrow(SystemRefusal.STATION_NOT_HERE_FOR_INSTANCE_MAIL::raise);
    }

    /**
     * @param dailyLimit how many mails a day the station may send through the instance's providers,
     *                   or null for no limit of its own
     */
    public record InstanceMailGrantRequest(@Nullable Integer dailyLimit) {}

    /**
     * @param stationUids the stations to grant
     * @param dailyLimit  how many mails a day each may send through the instance's providers, or
     *                    null for no limit of its own
     */
    public record InstanceMailBulkGrantRequest(
            List<UUID> stationUids, @Nullable Integer dailyLimit) {}

    /**
     * @param stationUids the stations to withdraw the instance's providers from
     */
    public record InstanceMailBulkWithdrawRequest(List<UUID> stationUids) {}
}
