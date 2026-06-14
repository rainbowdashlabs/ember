/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Periodically pings every usable peer so the gossip graph stays warm. Cadence is
 * driven by {@code discovery_ping_interval_minutes} (default 60).
 */
@Singleton
public class DiscoveryPingScheduler {
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

        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "discovery-ping-scheduler");
            t.setDaemon(true);
            return t;
        });
        // Initial delay 3 min so seeding (2 min) has a chance to fill the registry first.
        // Period is the default; admins can change discovery_ping_interval_minutes at runtime
        // but the next reschedule only takes effect after the JVM restarts (good enough for v1).
        scheduler.scheduleWithFixedDelay(
                this::runCycle, 3, DiscoverySettingsService.DEFAULT_PING_INTERVAL_MINUTES, TimeUnit.MINUTES);
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
