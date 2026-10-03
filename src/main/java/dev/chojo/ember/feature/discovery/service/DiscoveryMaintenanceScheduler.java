/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.auth.signing.DatabaseReplayStore;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPingRepository;
import dev.chojo.ember.feature.federation.entity.PairRequest;
import dev.chojo.ember.feature.federation.repository.PairRequestRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Periodic housekeeping for discovery state: every five minutes it drops the expired nonces of pings and of
 * every signed request from another instance, together with the requests to federate whose answer is older
 * than the cooldown after a decline, and once a day it decays negative reputations toward zero.
 */
@Singleton
public class DiscoveryMaintenanceScheduler implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryMaintenanceScheduler.class);
    private static final int REPUTATION_DECAY_STEP = 5;

    private final DiscoveryPingRepository pingRepository;
    private final DiscoveryPeerRepository peerRepository;
    private final DatabaseReplayStore replayStore;
    private final PairRequestRepository pairRequests;

    @Inject
    public DiscoveryMaintenanceScheduler(
            DiscoveryPingRepository pingRepository,
            DiscoveryPeerRepository peerRepository,
            DatabaseReplayStore replayStore,
            PairRequestRepository pairRequests) {
        this.pingRepository = pingRepository;
        this.peerRepository = peerRepository;
        this.replayStore = replayStore;
        this.pairRequests = pairRequests;
    }

    void forgetExpiredNonces() {
        try {
            int n = pingRepository.deleteExpired() + replayStore.forgetExpired();
            if (n > 0) log.debug("Discovery nonce GC: {} expired entries removed", n);
            int settled = pairRequests.deleteAnsweredBefore(Instant.now().minus(PairRequest.DECLINE_COOLDOWN));
            if (settled > 0) log.debug("Removed {} requests to federate answered long ago", settled);
        } catch (Exception e) {
            log.warn("Discovery nonce GC failed: {}", e.getMessage());
        }
    }

    void decayReputation() {
        try {
            int n = peerRepository.decayReputation(REPUTATION_DECAY_STEP);
            if (n > 0) log.debug("Discovery reputation decay: {} peer(s) pulled toward 0", n);
        } catch (Exception e) {
            log.warn("Discovery reputation decay failed: {}", e.getMessage());
        }
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(
                new ScheduledTask(
                        "signed-request-nonce-sweep",
                        Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(5)),
                        this::forgetExpiredNonces),
                new ScheduledTask(
                        "discovery-reputation-decay",
                        Schedule.fixedDelay(Duration.ofHours(1), Duration.ofDays(1)),
                        this::decayReputation));
    }
}
