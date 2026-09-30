/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.lifecycle.DelegatingTask;
import dev.chojo.ember.lifecycle.Schedule;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Refreshes the cached station listings of every reachable peer every 6 hours.
 */
@Singleton
public class DiscoveryStationRefreshScheduler extends DelegatingTask {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryStationRefreshScheduler.class);

    @Inject
    public DiscoveryStationRefreshScheduler(DiscoveryStationFetcher fetcher) {
        super(
                "discovery-station-refresh",
                Schedule.fixedDelay(Duration.ofMinutes(10), Duration.ofHours(6)),
                () -> refresh(fetcher));
    }

    private static void refresh(DiscoveryStationFetcher fetcher) {
        try {
            int n = fetcher.refreshAll();
            if (n > 0) log.debug("Discovery station refresh: {} card(s) fetched", n);
        } catch (Exception e) {
            log.warn("Discovery station refresh failed: {}", e.getMessage());
        }
    }
}
