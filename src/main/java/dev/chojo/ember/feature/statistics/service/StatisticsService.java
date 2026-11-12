/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.service;

import dev.chojo.ember.feature.signing.service.SigningKeyRecovery;
import dev.chojo.ember.feature.station.repository.StationOverviewRepository;
import dev.chojo.ember.feature.statistics.entity.AdminOverview;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.PeopleFigures;
import dev.chojo.ember.feature.statistics.entity.StationStatistics;
import dev.chojo.ember.feature.statistics.entity.StatisticsWindow;
import dev.chojo.ember.feature.statistics.repository.StatisticsRepository;
import dev.chojo.ember.feature.statistics.repository.StatisticsSeriesRepository;
import dev.chojo.ember.feature.statistics.repository.UsageRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The figures the statistics pages show, for one station and for the whole instance.
 */
@Singleton
public class StatisticsService {
    private final StatisticsRepository statistics;
    private final StatisticsSeriesRepository series;
    private final UsageRepository usage;
    private final SigningKeyRecovery signingKeys;
    private final StationOverviewRepository stations;

    @Inject
    public StatisticsService(
            StatisticsRepository statistics,
            StatisticsSeriesRepository series,
            UsageRepository usage,
            SigningKeyRecovery signingKeys,
            StationOverviewRepository stations) {
        this.statistics = statistics;
        this.series = series;
        this.usage = usage;
        this.signingKeys = signingKeys;
        this.stations = stations;
    }

    /**
     * @param stationId the station asking, whose figures alone are counted
     */
    public StationStatistics forStation(int stationId) {
        return statistics.stationStatistics(stationId);
    }

    /**
     * The figures of the whole instance: its totals as they stand, and how it is used and grows over
     * the window.
     *
     * @param window how far back the series reach
     */
    public AdminStatistics forInstance(StatisticsWindow window) {
        var people = new PeopleFigures(usage.peopleByType(), series.peopleByDay(window));
        var over = new InstanceSeries(
                series.usage(window),
                series.growth(window),
                series.attendanceByMonth(window),
                series.federation(window),
                people);
        return AdminStatistics.of(statistics.adminStatistics(), window, over);
    }

    /**
     * What needs an administrator's attention across the instance: the counts the database holds, the
     * stations waiting for their manager as the station list counts them, and the signing keys that no
     * longer open under the at-rest secret.
     */
    public AdminOverview overview() {
        return statistics
                .adminOverview()
                .withStationsSetupPending(stations.countWaiting())
                .withSigningKeysLocked(signingKeys.lockedCount());
    }
}
