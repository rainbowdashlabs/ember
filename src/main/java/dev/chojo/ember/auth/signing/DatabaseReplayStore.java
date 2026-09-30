/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth.signing;

import dev.chojo.ember.auth.signing.repository.SignedRequestNonceRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;

/**
 * Keeps nonces in the database, so they outlive a restart.
 *
 * <p>Used for discovery pings and beacon deliveries, which strangers send: anybody may ping or
 * report, and a restart in the middle of a window should not reopen it. One table serves every
 * protocol, told apart by the scope, and {@link #forgetExpired()} sweeps all of them at once.
 */
@Singleton
public class DatabaseReplayStore implements ReplayStore {
    private final SignedRequestNonceRepository repository;

    @Inject
    public DatabaseReplayStore(SignedRequestNonceRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean firstSighting(String scope, String nonce, Instant expiresAt) {
        return repository.record(scope, nonce, expiresAt);
    }

    /**
     * Forgets every nonce whose request could no longer be accepted.
     *
     * @return how many were forgotten
     */
    public int forgetExpired() {
        return repository.deleteExpired();
    }
}
