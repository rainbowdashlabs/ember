/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.beacon.repository.BeaconReadRepository;
import dev.chojo.ember.feature.beacon.service.BeaconSettings;
import dev.chojo.ember.feature.system.repository.ProblemReportRepository;
import dev.chojo.ember.feature.system.service.MonitoringCountService.MonitoringCounts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitoringCountServiceTest {
    private final ProblemReportRepository reports = mock(ProblemReportRepository.class);
    private final BeaconReadRepository beacon = mock(BeaconReadRepository.class);
    private final BeaconSettings settings = mock(BeaconSettings.class);

    @Test
    void aBeaconCountsWhatOtherInstancesSentItToo() {
        when(settings.receiving()).thenReturn(true);
        when(reports.countUnacknowledged()).thenReturn(2);
        when(beacon.countWaiting()).thenReturn(new BeaconReadRepository.Waiting(4, 5));

        var counts = new MonitoringCountService(reports, beacon, settings, () -> 3).counts();

        assertEquals(new MonitoringCounts(3, 2, true, 4, 5), counts);
    }

    @Test
    void anInstanceThatReceivesNothingDoesNotAskTheBeaconTables() {
        var counts = new MonitoringCountService(reports, beacon, settings).counts();

        assertEquals(0, counts.beaconFaults());
        assertEquals(false, counts.beaconActive());
        verify(beacon, never()).countWaiting();
    }
}
