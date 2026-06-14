/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Tracks the soft reputation score of every peer. Deltas are applied directly via
 * {@link DiscoveryPeerRepository#addReputation}; values are pulled toward zero by
 * {@link DiscoveryPeerRepository#decayReputation} on a daily cadence.
 *
 * <p>Numbers match {@code .concept/discovery.md} §7.
 */
@Singleton
public class DiscoveryReputationService {

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
    }

    public void recordSuccessfulFetch(String publicKey) {
        repository.addReputation(publicKey, DELTA_SUCCESSFUL_FETCH);
    }

    public void recordSignatureFailure(String publicKey) {
        repository.addReputation(publicKey, DELTA_SIGNATURE_FAILURE);
    }

    public void recordTimeout(String publicKey) {
        repository.addReputation(publicKey, DELTA_TIMEOUT);
    }

    public void recordInvalidAnnouncement(String publicKey) {
        repository.addReputation(publicKey, DELTA_INVALID_ANNOUNCEMENT);
    }

    public void upvote(String publicKey) {
        repository.addReputation(publicKey, DELTA_ADMIN_UPVOTE);
    }

    public void downvote(String publicKey) {
        repository.addReputation(publicKey, DELTA_ADMIN_DOWNVOTE);
    }
}
