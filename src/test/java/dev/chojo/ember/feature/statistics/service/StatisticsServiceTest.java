/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.service;

import dev.chojo.ember.feature.signing.service.SigningKeyRecovery;
import dev.chojo.ember.feature.station.repository.StationOverviewRepository;
import dev.chojo.ember.feature.statistics.entity.AdminOverview;
import dev.chojo.ember.feature.statistics.entity.InstanceTotals;
import dev.chojo.ember.feature.statistics.entity.PeopleByType;
import dev.chojo.ember.feature.statistics.entity.StatisticsWindow;
import dev.chojo.ember.feature.statistics.repository.StatisticsRepository;
import dev.chojo.ember.feature.statistics.repository.StatisticsSeriesRepository;
import dev.chojo.ember.feature.statistics.repository.UsageRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatisticsServiceTest {
    private final StatisticsRepository repository = mock(StatisticsRepository.class);
    private final StatisticsSeriesRepository series = mock(StatisticsSeriesRepository.class);
    private final UsageRepository usage = mock(UsageRepository.class);
    private final SigningKeyRecovery signingKeys = mock(SigningKeyRecovery.class);
    private final StationOverviewRepository stations = mock(StationOverviewRepository.class);
    private final StatisticsService service = new StatisticsService(repository, series, usage, signingKeys, stations);

    @Test
    void theFiguresAreCountedForTheStationAskingOrTheWholeInstance() {
        when(repository.adminOverview())
                .thenReturn(new AdminOverview(1, 2, 3, 4, 5, 6, 7, 8, 9, 0, List.of(), List.of()));
        when(signingKeys.lockedCount()).thenReturn(2);
        when(stations.countWaiting()).thenReturn(11);
        when(repository.adminStatistics())
                .thenReturn(new InstanceTotals(null, List.of(), List.of(), List.of(), List.of()));
        var people = new PeopleByType(5, 1, 2, 1, 1, 0, 6);
        when(usage.peopleByType()).thenReturn(people);

        service.forStation(4);
        var instance = service.forInstance(StatisticsWindow.MONTHS_12);
        var overview = service.overview();

        verify(repository).stationStatistics(4);
        verify(series).usage(StatisticsWindow.MONTHS_12);
        verify(series).growth(StatisticsWindow.MONTHS_12);
        verify(series).attendanceByMonth(StatisticsWindow.MONTHS_12);
        verify(series).federation(StatisticsWindow.MONTHS_12);
        verify(series).peopleByDay(StatisticsWindow.MONTHS_12);
        verify(repository).adminOverview();
        assertEquals(StatisticsWindow.MONTHS_12, instance.window());
        assertEquals(people, instance.peopleByType().current());
        assertEquals(2, overview.signingKeysLocked());
        assertEquals(9, overview.problemReportsOpen());
        assertEquals(
                11, overview.stationsSetupPending(), "waiting stations are counted as the station list counts them");
    }
}
