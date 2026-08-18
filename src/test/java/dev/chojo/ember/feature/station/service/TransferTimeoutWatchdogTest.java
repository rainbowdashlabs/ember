/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The watchdog's constructor performs the one-shot startup cleanup: every in-flight transfer
 * is treated as failed (via {@link StationExportService#abortAllInFlightTransfers()}) and any
 * orphan account left behind by a half-finished transfer is swept. The fixed-delay scheduler
 * is registered for the idle-timeout sweep but only fires on a 60-second cadence - the test
 * does not exercise the timer thread.
 */
class TransferTimeoutWatchdogTest {

    @Test
    void constructorRunsStartupCleanup() {
        var exportService = mock(StationExportService.class);
        var stationRepository = mock(StationRepository.class);

        new TransferTimeoutWatchdog(exportService, stationRepository);

        verify(exportService, times(1)).abortAllInFlightTransfers();
        verify(stationRepository, times(1)).sweepOrphanedAccounts();
    }

    @Test
    void constructorSwallowsAbortFailureAndContinuesWithSweep() {
        var exportService = mock(StationExportService.class);
        var stationRepository = mock(StationRepository.class);
        doThrow(new RuntimeException("db unreachable")).when(exportService).abortAllInFlightTransfers();

        new TransferTimeoutWatchdog(exportService, stationRepository);

        verify(exportService).abortAllInFlightTransfers();
        verify(stationRepository).sweepOrphanedAccounts();
    }

    @Test
    void constructorSwallowsOrphanSweepFailure() {
        var exportService = mock(StationExportService.class);
        var stationRepository = mock(StationRepository.class);
        doThrow(new RuntimeException("sweep failed")).when(stationRepository).sweepOrphanedAccounts();

        new TransferTimeoutWatchdog(exportService, stationRepository);

        verify(exportService).abortAllInFlightTransfers();
        verify(stationRepository).sweepOrphanedAccounts();
    }

    @Test
    void sweepStaleTransfersLogsClearedCount() {
        var exportService = mock(StationExportService.class);
        var stationRepository = mock(StationRepository.class);
        when(exportService.expireStaleTransfers(5)).thenReturn(3);

        var watchdog = new TransferTimeoutWatchdog(exportService, stationRepository);
        watchdog.sweepStaleTransfers();

        verify(exportService).expireStaleTransfers(5);
    }

    @Test
    void sweepStaleTransfersSwallowsExceptions() {
        var exportService = mock(StationExportService.class);
        var stationRepository = mock(StationRepository.class);
        when(exportService.expireStaleTransfers(5)).thenThrow(new RuntimeException("db down"));

        var watchdog = new TransferTimeoutWatchdog(exportService, stationRepository);
        watchdog.sweepStaleTransfers(); // must not throw

        verify(exportService).expireStaleTransfers(5);
    }

    @Test
    void sweepStaleTransfersWithNoClearedRowsStaysQuiet() {
        var exportService = mock(StationExportService.class);
        var stationRepository = mock(StationRepository.class);
        when(exportService.expireStaleTransfers(5)).thenReturn(0);

        var watchdog = new TransferTimeoutWatchdog(exportService, stationRepository);
        watchdog.sweepStaleTransfers();

        verify(exportService).expireStaleTransfers(5);
    }
}
