/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.lifecycle.DelegatingTask;
import dev.chojo.ember.lifecycle.Schedule;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Sends the access changes a guardian made once their waiting time has passed.
 *
 * <p>Runs once a minute, which is the resolution the waiting time is worth: it is measured in
 * minutes and exists to swallow a mistaken toggle, not to time anything precisely.
 */
@Singleton
public class ManagedLoginNoticeSweeper {
    private static final Logger log = LoggerFactory.getLogger(ManagedLoginNoticeSweeper.class);
    private static final Duration SCAN_INTERVAL = Duration.ofSeconds(60);

    private final ManagedLoginNoticeService noticeService;

    @Inject
    public ManagedLoginNoticeSweeper(ManagedLoginNoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /**
     * Body of the sweep, reachable by tests so they need not wait for the minute cadence. A failure
     * is logged and swallowed: whatever was due stays due and is tried again on the next run.
     */
    void sweep() {
        try {
            noticeService.dispatch();
        } catch (Exception e) {
            log.warn("Sweeping the pending access changes failed", e);
        }
    }

    /** Sends the access changes whose waiting time has passed, every minute. */
    @Singleton
    public static final class Task extends DelegatingTask {
        @Inject
        Task(ManagedLoginNoticeSweeper sweeper) {
            super("managed-login-notice-sweep", Schedule.fixedDelay(SCAN_INTERVAL, SCAN_INTERVAL), sweeper::sweep);
        }
    }
}
