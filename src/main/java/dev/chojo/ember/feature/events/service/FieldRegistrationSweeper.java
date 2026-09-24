/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Moves the far edge of the field registrations along as the days pass.
 *
 * <p>A question answered once for a whole series names the same people on every date, and a series
 * may have no last date, so the registrations it gives are written only as far ahead as anything can
 * see. Once a day the comparison runs again, which reaches one day further than it did yesterday and
 * keeps a subscribed calendar full to its far edge.
 *
 * <p>It also picks up anything that changed without going through the appointment's own screens: a
 * member removed from the station, a series shortened, a break added over a date somebody was down
 * for.
 */
@Singleton
public class FieldRegistrationSweeper {
    private static final Logger log = LoggerFactory.getLogger(FieldRegistrationSweeper.class);

    private final EventFieldRegistrationService registrationService;

    @Inject
    public FieldRegistrationSweeper(EventFieldRegistrationService registrationService) {
        this.registrationService = registrationService;

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "field-registration-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::sweep, 2, 60 * 12, TimeUnit.MINUTES);
    }

    private void sweep() {
        try {
            int events = registrationService.reconcileAll();
            log.debug("Compared the questions of {} appointments against their registrations", events);
        } catch (Exception e) {
            log.error("Failed to compare questions against registrations", e);
        }
    }
}
