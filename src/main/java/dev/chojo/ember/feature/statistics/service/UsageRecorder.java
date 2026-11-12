/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.service;

import dev.chojo.ember.feature.statistics.repository.UsageRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Counts the people using the instance each day, from the requests they make.
 *
 * <p>It runs inside an authenticated request, so it must never fail one: a request is worth more
 * than a figure on the statistics page. A failure is logged and swallowed, and the account is
 * counted on its next request that day.
 */
@Singleton
public class UsageRecorder {
    private static final Logger log = LoggerFactory.getLogger(UsageRecorder.class);

    private final UsageRepository usage;

    @Inject
    public UsageRecorder(UsageRepository usage) {
        this.usage = usage;
    }

    /**
     * Notes that an account used the instance today, which writes at most once a day per account.
     *
     * @param accountId the account
     */
    public void markActive(int accountId) {
        try {
            usage.markActive(accountId);
        } catch (RuntimeException e) {
            log.warn("Recording that account {} used the instance today failed", accountId, e);
        }
    }
}
