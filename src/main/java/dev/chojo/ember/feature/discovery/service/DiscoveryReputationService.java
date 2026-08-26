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

/**
 * Tracks the soft reputation score of every peer. Deltas are applied directly via
 * {@link DiscoveryPeerRepository#addReputation}; values are pulled toward zero by
 * {@link DiscoveryPeerRepository#decayReputation} on a daily cadence.
 */
@Singleton
public class DiscoveryReputationService {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryReputationService.class);

    public static final int DELTA_SUCCESSFUL_CALLBACK = 1;
    public static final int DELTA_SUCCESSFUL_FETCH = 1;
    public static final int DELTA_SIGNATURE_FAILURE = -20;
    public static final int DELTA_TIMEOUT = -1;
    public static final int DELTA_INVALID_ANNOUNCEMENT = -2;
    public static final int DELTA_ADMIN_UPVOTE = 50;
    public static final int DELTA_ADMIN_DOWNVOTE = -50;

    private final DiscoveryPeerRepository repository;

    @Inject
    public DiscoveryReputationService(DiscoveryPeerRepository repository) {
        this.repository = repository;
    }

    public void recordSuccessfulCallback(String publicKey) {
        repository.addReputation(publicKey, DELTA_SUCCESSFUL_CALLBACK);
        log.debug("Discovery peer {} answered a callback: applied delta {}", publicKey, DELTA_SUCCESSFUL_CALLBACK);
    }

    public void recordSuccessfulFetch(String publicKey) {
        repository.addReputation(publicKey, DELTA_SUCCESSFUL_FETCH);
        log.debug("Discovery peer {} answered a fetch: applied delta {}", publicKey, DELTA_SUCCESSFUL_FETCH);
    }

    public void recordSignatureFailure(String publicKey) {
        repository.addReputation(publicKey, DELTA_SIGNATURE_FAILURE);
        log.warn(
                "Discovery peer signature failure recorded for key {}: applied delta {}",
                publicKey,
                DELTA_SIGNATURE_FAILURE);
    }

    public void recordTimeout(String publicKey) {
        repository.addReputation(publicKey, DELTA_TIMEOUT);
        log.warn("Discovery peer {} timed out: applied delta {}", publicKey, DELTA_TIMEOUT);
    }

    public void recordInvalidAnnouncement(String publicKey) {
        repository.addReputation(publicKey, DELTA_INVALID_ANNOUNCEMENT);
        log.warn(
                "Discovery peer invalid announcement recorded for key {}: applied delta {}",
                publicKey,
                DELTA_INVALID_ANNOUNCEMENT);
    }

    public void upvote(String publicKey) {
        repository.addReputation(publicKey, DELTA_ADMIN_UPVOTE);
        log.info("Discovery peer upvoted by admin: key {}, delta {}", publicKey, DELTA_ADMIN_UPVOTE);
    }

    public void downvote(String publicKey) {
        repository.addReputation(publicKey, DELTA_ADMIN_DOWNVOTE);
        log.info("Discovery peer downvoted by admin: key {}, delta {}", publicKey, DELTA_ADMIN_DOWNVOTE);
    }
}
