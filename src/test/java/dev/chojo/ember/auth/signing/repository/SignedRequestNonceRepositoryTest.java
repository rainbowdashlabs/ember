/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth.signing.repository;

import dev.chojo.ember.auth.signing.DatabaseReplayStore;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nonces kept in the database: taken once per scope, and forgotten once their window has passed.
 */
class SignedRequestNonceRepositoryTest extends RepositoryTestBase {
    private final SignedRequestNonceRepository repository = new SignedRequestNonceRepository();
    private final DatabaseReplayStore store = new DatabaseReplayStore(repository);

    private static String nonce() {
        return UUID.randomUUID().toString();
    }

    @Test
    void aNonceIsTakenOncePerScope() {
        String nonce = nonce();
        Instant later = Instant.now().plusSeconds(600);

        assertTrue(store.firstSighting("discovery", nonce, later));
        assertFalse(store.firstSighting("discovery", nonce, later));
        assertTrue(store.firstSighting("beacon:abc", nonce, later));
    }

    @Test
    void expiredNoncesAreForgottenAndLiveOnesKept() {
        String expired = nonce();
        String live = nonce();
        store.firstSighting("discovery", expired, Instant.now().minusSeconds(60));
        store.firstSighting("discovery", live, Instant.now().plusSeconds(600));

        assertTrue(store.forgetExpired() >= 1);

        assertTrue(store.firstSighting("discovery", expired, Instant.now().plusSeconds(600)));
        assertFalse(store.firstSighting("discovery", live, Instant.now().plusSeconds(600)));
    }
}
