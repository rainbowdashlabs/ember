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

import java.time.Duration;
import java.time.Instant;

/**
 * Runs the expiry reminder sweep every thirty minutes, as the appointment reminders run theirs.
 *
 * <p>Only the clock lives here; what a sweep does is {@link ExpiryReminderService#sweep(Instant)}.
 */
@Singleton
public class ExpiryReminderChecker extends DelegatingTask {

    @Inject
    public ExpiryReminderChecker(ExpiryReminderService reminderService) {
        super(
                "expiry-reminder-check",
                Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(30)),
                () -> reminderService.sweep(Instant.now()));
    }
}
