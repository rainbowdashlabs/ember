/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.conf.file.elements.KnowledgeBase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Clears wiki entries out of the trash once their time there is up.
 *
 * <p>Runs hourly and asks only how old an entry is, never when the last run was, so an instance that
 * was off for a month catches up by itself rather than skipping what fell due while it slept.
 *
 * <p>That catching up is also why one run is capped. Clearing an entry out is file work, not a
 * statement, and the first run after a long outage could otherwise hold the boot up for minutes.
 * What does not fit goes an hour later, and nothing is lost by waiting.
 */
@Singleton
public class KbTrashPurger {
    private static final Logger log = LoggerFactory.getLogger(KbTrashPurger.class);
    private static final int SCAN_INTERVAL_MINUTES = 60;
    private static final int START_DELAY_MINUTES = 5;
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
        var scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "kb-trash-purger");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::purge, START_DELAY_MINUTES, SCAN_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Body of the run, reachable by tests so they need not wait for the hourly cadence. A failure is
     * logged and swallowed: what was due stays due and is tried again on the next run, whereas an
     * exception let through would end the schedule for as long as the instance is up.
     */
    void purge() {
        try {
            trashService.sweepExpired(config.trashRetentionDays(), MAX_PER_RUN);
        } catch (Exception e) {
            log.warn("Clearing the expired knowledge-base trash failed", e);
        }
    }
}
