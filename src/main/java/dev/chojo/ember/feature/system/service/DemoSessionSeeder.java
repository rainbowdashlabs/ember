/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Creates a handful of previous login sessions for the demo administrator so the active-session
 * list has entries from different browsers and devices.
 */
@Singleton
public class DemoSessionSeeder implements DemoSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoSessionSeeder.class);
    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",
            "Mozilla/5.0 (X11; Linux x86_64; rv:150.0) Gecko/20100101 Firefox/150.0");

    private final AccountRepository accountRepository;

    @Inject
    public DemoSessionSeeder(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seed(DemoRunContext run) {
        var sessionExpiry = Instant.now().plus(Duration.ofHours(24));
        for (String userAgent : USER_AGENTS) {
            var token = UUID.randomUUID().toString();
            accountRepository.createSession(run.adminAccount().id(), token, sessionExpiry, userAgent, null);
        }
        log.info("Demo: Created previous sessions");
    }
}
