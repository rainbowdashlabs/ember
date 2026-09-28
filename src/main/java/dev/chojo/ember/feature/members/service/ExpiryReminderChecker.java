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

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Runs the expiry reminder sweep every thirty minutes, as the appointment reminders run theirs.
 *
 * <p>Only the clock lives here; what a sweep does is {@link ExpiryReminderService#sweep(Instant)}.
 */
@Singleton
public class ExpiryReminderChecker {
    private static final Logger log = LoggerFactory.getLogger(ExpiryReminderChecker.class);

    @Inject
    public ExpiryReminderChecker(ExpiryReminderService reminderService) {
        var scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "expiry-reminder-checker");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        reminderService.sweep(Instant.now());
                    } catch (RuntimeException e) {
                        log.error("Error sending expiry reminders", e);
                    }
                },
                5,
                30,
                TimeUnit.MINUTES);
    }
}
