/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Removes expired sign-in state on a schedule: account tokens, sessions and WebAuthn challenges.
 *
 * <p>Tokens and sessions used to go away only when a lookup happened to consume them, which left
 * rows behind forever when nobody came back. The challenge table cannot even rely on that much:
 * an anonymous visitor can mint rows there, so it needs a sweep that runs whether anybody looks
 * or not.
 */
@Singleton
public class AuthCleanupSweeper {
    private static final Logger log = LoggerFactory.getLogger(AuthCleanupSweeper.class);
    private static final int SCAN_INTERVAL_MINUTES = 15;

    private final AccountRepository accountRepository;
    private final WebAuthnChallengeRepository challengeRepository;

    @Inject
    public AuthCleanupSweeper(AccountRepository accountRepository, WebAuthnChallengeRepository challengeRepository) {
        this.accountRepository = accountRepository;
        this.challengeRepository = challengeRepository;
        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "auth-cleanup-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::sweep, SCAN_INTERVAL_MINUTES, SCAN_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Body of the sweep, reachable by tests so they need not wait for the cadence. A failure is
     * logged and swallowed: whatever expired stays expired and goes on the next run.
     */
    void sweep() {
        try {
            accountRepository.deleteExpiredTokens();
            accountRepository.deleteExpiredSessions();
            int challenges = challengeRepository.deleteExpired();
            if (challenges > 0) {
                log.debug("Swept {} expired WebAuthn challenges", challenges);
            }
        } catch (Exception e) {
            log.warn("Sweeping expired sign-in state failed", e);
        }
    }
}
