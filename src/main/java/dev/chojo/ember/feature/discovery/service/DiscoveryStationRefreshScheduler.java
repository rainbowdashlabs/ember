/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Refreshes the cached station listings of every reachable peer every 6 hours.
 */
@Singleton
public class DiscoveryStationRefreshScheduler implements TaskSource {
    /** How long after one refresh has finished the next one starts. */
    public static final Duration REFRESH_INTERVAL = Duration.ofHours(6);

    private static final Logger log = LoggerFactory.getLogger(DiscoveryStationRefreshScheduler.class);

    private final DiscoveryStationFetcher fetcher;

    @Inject
    public DiscoveryStationRefreshScheduler(DiscoveryStationFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "discovery-station-refresh",
                Schedule.fixedDelay(Duration.ofMinutes(10), REFRESH_INTERVAL),
                this::refresh));
    }

    private void refresh() {
        try {
            int n = fetcher.refreshAll();
            if (n > 0) log.debug("Discovery station refresh: {} card(s) fetched", n);
        } catch (Exception e) {
            log.warn("Discovery station refresh failed: {}", e.getMessage());
        }
    }
}
