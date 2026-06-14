/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Refreshes the cached station listings of every reachable peer every 6 hours.
 */
@Singleton
public class DiscoveryStationRefreshScheduler {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryStationRefreshScheduler.class);

    @Inject
    public DiscoveryStationRefreshScheduler(DiscoveryStationFetcher fetcher) {
        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "discovery-station-refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        int n = fetcher.refreshAll();
                        if (n > 0) log.debug("Discovery station refresh: {} card(s) fetched", n);
                    } catch (Exception e) {
                        log.warn("Discovery station refresh failed: {}", e.getMessage());
                    }
                },
                10,
                6 * 60L,
                TimeUnit.MINUTES);
    }
}
