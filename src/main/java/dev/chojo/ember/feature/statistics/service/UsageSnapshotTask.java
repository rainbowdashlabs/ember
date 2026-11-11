/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.service;

import dev.chojo.ember.feature.statistics.repository.UsageRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Writes the day's snapshot of how large the instance is, every hour.
 *
 * <p>Accounts, stations, members and most other records carry no creation time, so their growth
 * cannot be worked out afterwards; this snapshot is the only history of it. Each run replaces the
 * day's earlier one, so the last run of a day is what the day keeps. The first run comes shortly
 * after startup, so an instance restarted every night still records its days.
 */
@Singleton
public class UsageSnapshotTask implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(UsageSnapshotTask.class);
    private static final Duration FIRST_RUN = Duration.ofMinutes(2);
    private static final Duration INTERVAL = Duration.ofHours(1);

    private final UsageRepository usage;

    @Inject
    public UsageSnapshotTask(UsageRepository usage) {
        this.usage = usage;
    }

    /**
     * Body of the task, reachable by tests so they need not wait for the cadence. The mails are
     * counted by the application's calendar, the one the mail queue writes its daily count in. A
     * failure is logged and swallowed; the next run writes the day again.
     */
    void snapshot() {
        try {
            usage.snapshot(LocalDate.now());
        } catch (RuntimeException e) {
            log.warn("Writing today's usage snapshot failed", e);
        }
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask("usage-snapshot", Schedule.fixedDelay(FIRST_RUN, INTERVAL), this::snapshot));
    }
}
