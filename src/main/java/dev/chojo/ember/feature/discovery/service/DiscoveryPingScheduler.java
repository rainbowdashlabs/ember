/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
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
 * Periodically pings every usable peer so the gossip graph stays warm, every
 * {@link DiscoverySettingsService#DEFAULT_PING_INTERVAL_MINUTES} minutes.
 *
 * <p>The first run waits three minutes so the partner seeding, two minutes after the start, has a chance
 * to fill the registry first. Administrators can change {@code discovery_ping_interval_minutes} at
 * runtime, but the cadence stays the default until the next restart, which is good enough for now.
 */
@Singleton
public class DiscoveryPingScheduler implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryPingScheduler.class);

    private final DiscoveryPeerRepository peerRepository;
    private final DiscoveryPingService pingService;
    private final DiscoverySettingsService settingsService;

    @Inject
    public DiscoveryPingScheduler(
            DiscoveryPeerRepository peerRepository,
            DiscoveryPingService pingService,
            DiscoverySettingsService settingsService) {
        this.peerRepository = peerRepository;
        this.pingService = pingService;
        this.settingsService = settingsService;
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "discovery-ping",
                Schedule.fixedDelay(
                        Duration.ofMinutes(3),
                        Duration.ofMinutes(DiscoverySettingsService.DEFAULT_PING_INTERVAL_MINUTES)),
                this::runCycle));
    }

    private void runCycle() {
        if (!settingsService.isEnabled()) return;
        try {
            var peers = peerRepository.findUsable();
            if (peers.isEmpty()) return;
            for (var peer : peers) {
                try {
                    pingService.sendPing(peer);
                } catch (Exception e) {
                    log.debug("Failed to ping {}: {}", peer.baseUrl(), e.getMessage());
                }
            }
            log.debug("Discovery ping cycle: {} peer(s) pinged", peers.size());
        } catch (Exception e) {
            log.warn("Discovery ping cycle failed: {}", e.getMessage());
        }
    }
}
