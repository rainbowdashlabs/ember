/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.conf.file.elements.KnowledgeBase;
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
 * Clears wiki entries out of the trash once their time there is up.
 *
 * <p>Runs hourly and asks only how old an entry is, never when the last run was, so an instance that
 * was off for a month catches up by itself rather than skipping what fell due while it slept.
 *
 * <p>That catching up is also why one run is capped. Clearing an entry out is file work, not a
 * statement, and the first run after a long outage could otherwise hold its thread for minutes.
 * What does not fit goes an hour later, and nothing is lost by waiting.
 */
@Singleton
public class KbTrashPurger implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(KbTrashPurger.class);
    private static final Duration SCAN_INTERVAL = Duration.ofMinutes(60);
    private static final Duration START_DELAY = Duration.ofMinutes(5);
    /**
     * How many entries one run clears out. Each one takes a file operation per article inside it, so
     * this is a ceiling on how long a single run can hold a thread.
     */
    static final int MAX_PER_RUN = 500;

    private final KbTrashService trashService;
    private final KnowledgeBase config;

    @Inject
    public KbTrashPurger(KbTrashService trashService, KnowledgeBase config) {
        this.trashService = trashService;
        this.config = config;
    }

    /**
     * Body of the run, reachable by tests so they need not wait for the hourly cadence. A failure is
     * logged and swallowed: what was due stays due and is tried again on the next run.
     */
    void purge() {
        try {
            trashService.sweepExpired(config.trashRetentionDays(), MAX_PER_RUN);
        } catch (Exception e) {
            log.warn("Clearing the expired knowledge-base trash failed", e);
        }
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(
                new ScheduledTask("kb-trash-purge", Schedule.fixedDelay(START_DELAY, SCAN_INTERVAL), this::purge));
    }
}
