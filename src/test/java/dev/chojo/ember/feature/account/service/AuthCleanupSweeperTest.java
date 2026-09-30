/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.devicerequest.repository.DeviceRequestRepository;
import dev.chojo.ember.feature.twofactor.entity.ChallengePurpose;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthCleanupSweeperTest extends RepositoryTestBase {

    @Test
    void theTaskSweepsEveryFifteenMinutes() {
        var sweeper = mock(AuthCleanupSweeper.class);
        when(sweeper.scheduledTasks()).thenCallRealMethod();
        var task = sweeper.scheduledTasks().getFirst();

        task.work().run();

        verify(sweeper).sweep();
        assertEquals("auth-cleanup-sweep", task.name());
        assertEquals(Duration.ofMinutes(15), task.schedule().period());
    }

    @Test
    void aFailedSweepIsSwallowed() {
        var accounts = mock(AccountRepository.class);
        doThrow(new IllegalStateException("database gone")).when(accounts).deleteExpiredTokens();
        var sweeper = new AuthCleanupSweeper(
                accounts, mock(WebAuthnChallengeRepository.class), mock(DeviceRequestRepository.class));

        assertDoesNotThrow(sweeper::sweep);
    }

    @Test
    void sweepRemovesExpiredTokensSessionsAndChallenges() {
        var challengeRepo = new WebAuthnChallengeRepository(TokenHasher.forTesting("repository-test-pepper"));
        var deviceRequestRepo = new dev.chojo.ember.feature.devicerequest.repository.DeviceRequestRepository();
        var sweeper = new AuthCleanupSweeper(accountRepo, challengeRepo, deviceRequestRepo);

        int accountId = accountRepo
                .create("sweeper-" + UUID.randomUUID() + "@test.com", "Sweep", "Er", true)
                .id();
        Instant past = Instant.now().minusSeconds(60);
        Instant future = Instant.now().plusSeconds(600);

        String expiredToken = "expired-token-" + UUID.randomUUID();
        String liveToken = "live-token-" + UUID.randomUUID();
        accountRepo.createToken(accountId, expiredToken, TokenType.VERIFY_EMAIL, past);
        accountRepo.createToken(accountId, liveToken, TokenType.VERIFY_EMAIL, future);

        String expiredSession = "expired-session-" + UUID.randomUUID();
        String liveSession = "live-session-" + UUID.randomUUID();
        accountRepo.createSession(accountId, expiredSession, past, "ua", null);
        accountRepo.createSession(accountId, liveSession, future, "ua", null);

        String expiredChallenge = "expired-challenge-" + UUID.randomUUID();
        challengeRepo.create(expiredChallenge, ChallengePurpose.REGISTRATION, accountId, "{}", past);

        String expiredPollSecret = "expired-poll-" + UUID.randomUUID();
        deviceRequestRepo.create(
                dev.chojo.ember.feature.devicerequest.entity.DeviceRequestPurpose.ENROL_PASSKEY,
                "code-hash-" + UUID.randomUUID(),
                expiredPollSecret,
                accountId,
                42,
                List.of(13, 27, 42, 58, 71, 86),
                "ua",
                null,
                past);

        sweeper.sweep();

        assertTrue(
                deviceRequestRepo.findByPollSecret(expiredPollSecret).isEmpty(),
                "the sweep must take the expired device request");

        assertTrue(accountRepo.findToken(expiredToken).isEmpty(), "the sweep must take the expired token");
        assertTrue(accountRepo.findToken(liveToken).isPresent(), "the sweep must not touch a live token");
        assertTrue(accountRepo.findSession(expiredSession).isEmpty(), "the sweep must take the expired session");
        assertTrue(accountRepo.findSession(liveSession).isPresent(), "the sweep must not touch a live session");
        assertTrue(challengeRepo.consume(expiredChallenge).isEmpty(), "the sweep must take the expired challenge");
    }
}
