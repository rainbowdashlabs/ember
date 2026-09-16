/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.system.repository.ProblemReportRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Lets go of problem reports a month after somebody marked them dealt with.
 *
 * <p>Reports were kept for good, which nobody decided and everybody would have argued with: they
 * carry what a person wrote, which page they were on, and now a picture of it. Marking one dealt
 * with is somebody saying they have finished with it, and a month after that there is no reason
 * left to hold any of it.
 *
 * <p>The date rather than the flag is what counts, so a report stays readable for the month after
 * it was dealt with. What was never dealt with is never swept: it is still somebody's to answer.
 */
@Singleton
public class ProblemReportSweeper {
    private static final Logger log = LoggerFactory.getLogger(ProblemReportSweeper.class);
    private static final Duration KEEP_AFTER_ACKNOWLEDGED = Duration.ofDays(30);
    private static final int SCAN_INTERVAL_HOURS = 6;

    private final ProblemReportRepository repository;
    private final ProblemReportScreenshotService screenshots;

    @Inject
    public ProblemReportSweeper(ProblemReportRepository repository, ProblemReportScreenshotService screenshots) {
        this.repository = repository;
        this.screenshots = screenshots;
        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "problem-report-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::sweep, 1, SCAN_INTERVAL_HOURS, TimeUnit.HOURS);
    }

    /**
     * Body of the sweep, reachable by tests so they need not wait for the cadence.
     *
     * <p>The picture goes with the row rather than after it: a row deleted whose bytes are left
     * behind is a picture nothing points at any more and nothing will ever come back for.
     */
    void sweep() {
        try {
            var done = repository.findAcknowledgedBefore(Instant.now().minus(KEEP_AFTER_ACKNOWLEDGED));
            for (var report : done) {
                repository.delete(report.id());
                screenshots.forget(report.screenshotFileId());
            }
            if (!done.isEmpty()) {
                log.info("Swept {} problem reports dealt with over {} ago", done.size(), KEEP_AFTER_ACKNOWLEDGED);
            }
        } catch (Exception e) {
            log.warn("Sweeping problem reports failed", e);
        }
    }
}
