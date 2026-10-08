/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.service;

import dev.chojo.ember.feature.signing.service.SigningKeyRecovery;
import dev.chojo.ember.feature.statistics.entity.AdminOverview;
import dev.chojo.ember.feature.statistics.repository.StatisticsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatisticsServiceTest {
    private final StatisticsRepository repository = mock(StatisticsRepository.class);
    private final SigningKeyRecovery signingKeys = mock(SigningKeyRecovery.class);
    private final StatisticsService service = new StatisticsService(repository, signingKeys);

    @Test
    void theFiguresAreCountedForTheStationAskingOrTheWholeInstance() {
        when(repository.adminOverview())
                .thenReturn(new AdminOverview(1, 2, 3, 4, 5, 6, 7, 8, 9, 0, List.of(), List.of()));
        when(signingKeys.lockedCount()).thenReturn(2);

        service.forStation(4);
        service.forInstance();
        var overview = service.overview();

        verify(repository).stationStatistics(4);
        verify(repository).adminStatistics();
        verify(repository).adminOverview();
        assertEquals(2, overview.signingKeysLocked());
        assertEquals(9, overview.problemReportsOpen());
    }
}
