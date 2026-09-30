/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.devicerequest.repository.DeviceRequestRepository;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Removes expired sign-in state on a schedule: account tokens, sessions and WebAuthn challenges.
 *
 * <p>Tokens and sessions used to go away only when a lookup happened to consume them, which left
 * rows behind forever when nobody came back. The challenge table cannot even rely on that much:
 * an anonymous visitor can mint rows there, so it needs a sweep that runs whether anybody looks
 * or not.
 */
@Singleton
public class AuthCleanupSweeper implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(AuthCleanupSweeper.class);
    private static final Duration SCAN_INTERVAL = Duration.ofMinutes(15);

    private final AccountRepository accountRepository;
    private final WebAuthnChallengeRepository challengeRepository;
    private final DeviceRequestRepository deviceRequestRepository;

    @Inject
    public AuthCleanupSweeper(
            AccountRepository accountRepository,
            WebAuthnChallengeRepository challengeRepository,
            DeviceRequestRepository deviceRequestRepository) {
        this.accountRepository = accountRepository;
        this.challengeRepository = challengeRepository;
        this.deviceRequestRepository = deviceRequestRepository;
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
            int deviceRequests = deviceRequestRepository.deleteExpired();
            if (deviceRequests > 0) {
                log.debug("Swept {} expired device requests", deviceRequests);
            }
        } catch (Exception e) {
            log.warn("Sweeping expired sign-in state failed", e);
        }
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "auth-cleanup-sweep", Schedule.fixedDelay(SCAN_INTERVAL, SCAN_INTERVAL), this::sweep));
    }
}
