/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.service;

import dev.chojo.ember.feature.statistics.repository.StatisticsRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StatisticsServiceTest {
    private final StatisticsRepository repository = mock(StatisticsRepository.class);
    private final StatisticsService service = new StatisticsService(repository);

    @Test
    void theFiguresAreCountedForTheStationAskingOrTheWholeInstance() {
        service.forStation(4);
        service.forInstance();
        service.overview();

        verify(repository).stationStatistics(4);
        verify(repository).adminStatistics();
        verify(repository).adminOverview();
    }
}
