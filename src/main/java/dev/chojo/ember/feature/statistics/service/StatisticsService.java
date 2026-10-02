/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.service;

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

    @Inject
    public StatisticsService(StatisticsRepository statistics) {
        this.statistics = statistics;
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
     * What needs an administrator's attention across the instance.
     */
    public AdminOverview overview() {
        return statistics.adminOverview();
    }
}
