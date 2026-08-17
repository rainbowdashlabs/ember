/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Watches in-flight cross-instance transfers and invalidates tokens whose destination has
 * gone silent. The destination touches {@code transfer_token.last_activity_at} on every
 * token-authenticated request via {@link StationExportService#validateToken(String)}; this
 * watchdog scans once a minute for tokens that have not been touched for more than
 * {@link #IDLE_TIMEOUT_MINUTES} minutes, marks them used, and clears the source station's
 * read-only-for-transfer flag - so a destination that crashes or hangs mid-pull cannot
 * leave the source station locked until the 24-hour token expiry.
 *
 * <p>The constructor immediately aborts in-flight transfers and sweeps orphaned accounts,
 * so this class must be instantiated from the bootstrapper after the database configuration
 * is initialised - never bound as an eager singleton, which would construct it during
 * injector creation before {@code QueryConfiguration.setDefault()} has run.
 */
@Singleton
public class TransferTimeoutWatchdog {
    private static final Logger log = LoggerFactory.getLogger(TransferTimeoutWatchdog.class);
    private static final int IDLE_TIMEOUT_MINUTES = 5;
    private static final int SCAN_INTERVAL_SECONDS = 60;

    private final StationExportService exportService;

    @Inject
    public TransferTimeoutWatchdog(StationExportService exportService, StationRepository stationRepository) {
        this.exportService = exportService;
        try {
            exportService.abortAllInFlightTransfers();
        } catch (Exception e) {
            log.warn("Startup transfer cleanup failed: {}", e.getMessage());
        }
        try {
            stationRepository.sweepOrphanedAccounts();
        } catch (Exception e) {
            log.warn("Startup orphan-account sweep failed: {}", e.getMessage());
        }
        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "transfer-timeout-watchdog");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(
                this::sweepStaleTransfers, SCAN_INTERVAL_SECONDS, SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Body of the scheduled sweep - extracted so tests can drive it directly without waiting for
     * the executor's 60-second cadence. Logs and continues on any failure; the safety net is the
     * 24-hour token expiry on the source side.
     */
    void sweepStaleTransfers() {
        try {
            int cleared = exportService.expireStaleTransfers(IDLE_TIMEOUT_MINUTES);
            if (cleared > 0) {
                log.info("Transfer timeout sweep: cleared read-only flag on {} station(s)", cleared);
            }
        } catch (Exception e) {
            log.warn("Transfer timeout sweep failed: {}", e.getMessage());
        }
    }
}
