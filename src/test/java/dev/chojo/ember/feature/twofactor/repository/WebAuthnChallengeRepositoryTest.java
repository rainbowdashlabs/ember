/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.repository;

import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.twofactor.entity.ChallengePurpose;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebAuthnChallengeRepositoryTest extends RepositoryTestBase {

    private static WebAuthnChallengeRepository repo;

    @BeforeAll
    static void setup() {
        repo = new WebAuthnChallengeRepository(TokenHasher.forTesting("repository-test-pepper"));
    }

    private int newAccount() {
        return accountRepo
                .create("wa-challenge-" + UUID.randomUUID() + "@test.com", "WA", "Challenge", true)
                .id();
    }

    private static String newToken() {
        return "token-" + UUID.randomUUID();
    }

    @Test
    void createAndConsumeRoundtrip() {
        int accountId = newAccount();
        String token = newToken();
        repo.create(
                token,
                ChallengePurpose.REGISTRATION,
                accountId,
                "{\"options\":true}",
                Instant.now().plusSeconds(300));

        var challenge = repo.consume(token).orElseThrow();
        assertEquals(ChallengePurpose.REGISTRATION, challenge.purpose());
        assertEquals(accountId, challenge.accountId());
        assertEquals("{\"options\":true}", challenge.optionsJson());
        assertFalse(challenge.isExpired());
    }

    @Test
    void consumeIsSingleUse() {
        int accountId = newAccount();
        String token = newToken();
        repo.create(
                token,
                ChallengePurpose.SECOND_FACTOR_ASSERTION,
                accountId,
                "{}",
                Instant.now().plusSeconds(300));

        assertTrue(repo.consume(token).isPresent());
        assertTrue(repo.consume(token).isEmpty(), "a consumed challenge must not be spendable twice");
    }

    @Test
    void consumeUnknownTokenIsEmpty() {
        assertTrue(repo.consume("no-such-token").isEmpty());
    }

    @Test
    void passwordlessChallengeCarriesNoAccount() {
        String token = newToken();
        repo.create(
                token,
                ChallengePurpose.PASSKEY_SIGN_IN,
                null,
                "{}",
                Instant.now().plusSeconds(300));

        var challenge = repo.consume(token).orElseThrow();
        assertEquals(ChallengePurpose.PASSKEY_SIGN_IN, challenge.purpose());
        assertNull(challenge.accountId());
    }

    @Test
    void expiredChallengeComesBackExpiredAndConsumed() {
        int accountId = newAccount();
        String token = newToken();
        repo.create(
                token,
                ChallengePurpose.REGISTRATION,
                accountId,
                "{}",
                Instant.now().minusSeconds(60));

        var challenge = repo.consume(token).orElseThrow();
        assertTrue(challenge.isExpired());
        assertTrue(repo.consume(token).isEmpty());
    }

    @Test
    void deleteExpiredSweepsOnlyThePast() {
        int accountId = newAccount();
        String expired = newToken();
        String live = newToken();
        repo.create(
                expired,
                ChallengePurpose.REGISTRATION,
                accountId,
                "{}",
                Instant.now().minusSeconds(60));
        repo.create(
                live,
                ChallengePurpose.REGISTRATION,
                accountId,
                "{}",
                Instant.now().plusSeconds(300));

        assertTrue(repo.deleteExpired() >= 1);
        assertTrue(repo.consume(expired).isEmpty(), "the sweep must have taken the expired row");
        assertTrue(repo.consume(live).isPresent(), "the sweep must not touch a live challenge");
    }
}
