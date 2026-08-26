/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Sends the access changes a guardian made once their waiting time has passed.
 *
 * <p>Runs once a minute, which is the resolution the waiting time is worth: it is measured in
 * minutes and exists to swallow a mistaken toggle, not to time anything precisely.
 */
@Singleton
public class ManagedLoginNoticeSweeper {
    private static final Logger log = LoggerFactory.getLogger(ManagedLoginNoticeSweeper.class);
    private static final int SCAN_INTERVAL_SECONDS = 60;

    private final ManagedLoginNoticeService noticeService;

    @Inject
    public ManagedLoginNoticeSweeper(ManagedLoginNoticeService noticeService) {
        this.noticeService = noticeService;
        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "managed-login-notice-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::sweep, SCAN_INTERVAL_SECONDS, SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
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
}
