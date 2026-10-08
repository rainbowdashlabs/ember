/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.service;

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
 * Marks the link requests nobody answered within thirty days as expired, so the station that asked
 * sees it and may send them again.
 *
 * <p>Hourly is plenty: a request whose time is up is already refused when somebody tries to answer it,
 * and the sweep only puts that into the row.
 */
@Singleton
public class AccountLinkSweeper implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(AccountLinkSweeper.class);
    private static final Duration INTERVAL = Duration.ofHours(1);

    private final AccountLinkService linkService;

    @Inject
    public AccountLinkSweeper(AccountLinkService linkService) {
        this.linkService = linkService;
    }

    /**
     * Body of the sweep, reachable by tests. A failure is logged and swallowed: the requests stay due
     * and are tried again on the next run.
     */
    void sweep() {
        try {
            linkService.expireOverdue();
        } catch (Exception e) {
            log.warn("Sweeping the expired link requests failed", e);
        }
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "account-link-expiry-sweep", Schedule.fixedDelay(Duration.ofMinutes(5), INTERVAL), this::sweep));
    }
}
