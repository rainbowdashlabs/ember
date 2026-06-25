/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.HibpSettings;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous post-login HIBP breach check. After a successful password
 * verification the caller submits {@code (accountId, plaintext)} via
 * {@link #enqueueCheck(int, String)}; the worker performs the lookup on a virtual
 * thread so the login response is not delayed.
 * <p>
 * The check is skipped when the credential's {@code last_breach_check_at} is
 * within the configured staleness window — re-checking the same password every
 * login would be wasteful. The window is reset to {@code NULL} whenever the
 * password is rotated, so a freshly-set password is always checked on its first
 * successful login.
 * <p>
 * A pwned result sets {@code force_password_change = TRUE} so the user is
 * routed through the existing forced-rotation flow on their next login.
 * The plaintext is held only inside the worker task and discarded when the task
 * completes.
 */
@Singleton
public class BreachCheckWorker {
    private static final Logger log = LoggerFactory.getLogger(BreachCheckWorker.class);

    private final HibpClient hibpClient;
    private final AccountRepository accountRepository;
    private final HibpSettings config;
    private final ExecutorService executor;

    @Inject
    public BreachCheckWorker(HibpClient hibpClient, AccountRepository accountRepository, Auth authConfig) {
        this(hibpClient, accountRepository, authConfig.hibp(), Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Visible-for-testing constructor that lets tests substitute a synchronous executor.
     */
    public BreachCheckWorker(
            HibpClient hibpClient, AccountRepository accountRepository, HibpSettings config, ExecutorService executor) {
        this.hibpClient = hibpClient;
        this.accountRepository = accountRepository;
        this.config = config;
        this.executor = executor;
    }

    /**
     * Schedules a breach check for the given account and plaintext. Fire-and-forget
     * — the call returns immediately and the lookup runs asynchronously. A no-op
     * when HIBP is disabled by configuration.
     */
    public void enqueueCheck(int accountId, String plaintext) {
        if (!config.enabled() || plaintext == null || plaintext.isEmpty()) {
            return;
        }
        try {
            executor.submit(() -> runCheck(accountId, plaintext));
        } catch (Exception e) {
            log.warn("Failed to submit breach check for account {}: {}", accountId, e.getMessage());
        }
    }

    private void runCheck(int accountId, String plaintext) {
        try {
            var credOpt = accountRepository.findCredential(accountId);
            if (credOpt.isEmpty()) return;
            var cred = credOpt.get();
            Duration staleAfter = Duration.ofDays(Math.max(1, config.staleAfterDays()));
            if (cred.lastBreachCheckAt() != null
                    && cred.lastBreachCheckAt().isAfter(Instant.now().minus(staleAfter))) {
                return;
            }
            boolean pwned = hibpClient.isPwned(plaintext);
            accountRepository.updateLastBreachCheck(accountId, Instant.now(), pwned);
            if (pwned) {
                log.info("HIBP flagged account {} as pwned; force-password-change set", accountId);
            }
        } catch (Exception e) {
            log.warn("Breach check failed for account {}: {}", accountId, e.getMessage());
        }
    }
}
