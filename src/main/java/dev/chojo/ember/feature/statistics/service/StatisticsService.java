/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.service;

import dev.chojo.ember.feature.signing.service.SigningKeyRecovery;
import dev.chojo.ember.feature.statistics.entity.AdminOverview;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics;
import dev.chojo.ember.feature.statistics.entity.StationStatistics;
import dev.chojo.ember.feature.statistics.repository.StatisticsRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The figures the statistics pages show, for one station and for the whole instance.
 */
@Singleton
public class StatisticsService {
    private final StatisticsRepository statistics;
    private final SigningKeyRecovery signingKeys;

    @Inject
    public StatisticsService(StatisticsRepository statistics, SigningKeyRecovery signingKeys) {
        this.statistics = statistics;
        this.signingKeys = signingKeys;
    }

    /**
     * @param stationId the station asking, whose figures alone are counted
     */
    public StationStatistics forStation(int stationId) {
        return statistics.stationStatistics(stationId);
    }

    public AdminStatistics forInstance() {
        return statistics.adminStatistics();
    }

    /**
     * What needs an administrator's attention across the instance: the counts the database holds, and the
     * signing keys that no longer open under the at-rest secret.
     */
    public AdminOverview overview() {
        return statistics.adminOverview().withSigningKeysLocked(signingKeys.lockedCount());
    }
}
