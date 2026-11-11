/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.beacon.repository.BeaconReadRepository;
import dev.chojo.ember.feature.beacon.service.BeaconSettings;
import dev.chojo.ember.feature.mail.service.FailedMailService;
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
    private final FailedMailService failedMails = mock(FailedMailService.class);

    @Test
    void aBeaconCountsWhatOtherInstancesSentItToo() {
        when(settings.receiving()).thenReturn(true);
        when(reports.countUnacknowledged()).thenReturn(2);
        when(beacon.countWaiting()).thenReturn(new BeaconReadRepository.Waiting(4, 5));
        when(failedMails.unacknowledgedCount()).thenReturn(6);

        var counts = new MonitoringCountService(reports, beacon, settings, failedMails, () -> 3).counts();

        assertEquals(new MonitoringCounts(3, 2, true, 4, 5, 6), counts);
    }

    @Test
    void anInstanceThatReceivesNothingDoesNotAskTheBeaconTables() {
        var counts = new MonitoringCountService(reports, beacon, settings, failedMails).counts();

        assertEquals(0, counts.beaconFaults());
        assertEquals(false, counts.beaconActive());
        verify(beacon, never()).countWaiting();
    }

    /** The failed mails count whether or not the instance is a beacon, and only those nobody took note of. */
    @Test
    void failedMailsAreCountedOnEveryInstance() {
        when(failedMails.unacknowledgedCount()).thenReturn(2);

        var counts = new MonitoringCountService(reports, beacon, settings, failedMails, () -> 0).counts();

        assertEquals(2, counts.failedMails());
    }
}
