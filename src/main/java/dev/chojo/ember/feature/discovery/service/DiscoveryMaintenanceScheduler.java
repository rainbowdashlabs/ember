/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Periodic housekeeping for discovery state.
 *
 * <ul>
 *   <li>Drops expired ping nonces every 5 minutes (§9 {@code DiscoveryNonceGc}).</li>
 *   <li>Decays negative reputations toward zero every 24 hours (§9 {@code
 *       DiscoveryReputationDecay}).</li>
 * </ul>
 */
@Singleton
public class DiscoveryMaintenanceScheduler {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryMaintenanceScheduler.class);
    private static final int REPUTATION_DECAY_STEP = 5;

    @Inject
    public DiscoveryMaintenanceScheduler(
            DiscoveryPingRepository pingRepository, DiscoveryPeerRepository peerRepository) {
        var nonceGc = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "discovery-nonce-gc");
            t.setDaemon(true);
            return t;
        });
        nonceGc.scheduleWithFixedDelay(
                () -> {
                    try {
                        int n = pingRepository.deleteExpired();
                        if (n > 0) log.debug("Discovery nonce GC: {} expired entries removed", n);
                    } catch (Exception e) {
                        log.warn("Discovery nonce GC failed: {}", e.getMessage());
                    }
                },
                5,
                5,
                TimeUnit.MINUTES);

        var decay = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "discovery-reputation-decay");
            t.setDaemon(true);
            return t;
        });
        decay.scheduleWithFixedDelay(
                () -> {
                    try {
                        int n = peerRepository.decayReputation(REPUTATION_DECAY_STEP);
                        if (n > 0) log.debug("Discovery reputation decay: {} peer(s) pulled toward 0", n);
                    } catch (Exception e) {
                        log.warn("Discovery reputation decay failed: {}", e.getMessage());
                    }
                },
                60,
                24 * 60L,
                TimeUnit.MINUTES);
    }
}
