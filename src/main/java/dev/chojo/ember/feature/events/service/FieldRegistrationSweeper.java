/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

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
public class FieldRegistrationSweeper implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(FieldRegistrationSweeper.class);

    private final EventFieldRegistrationService registrationService;

    @Inject
    public FieldRegistrationSweeper(EventFieldRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    private void sweep() {
        try {
            int events = registrationService.reconcileAll();
            log.debug("Compared the questions of {} appointments against their registrations", events);
        } catch (Exception e) {
            log.error("Failed to compare questions against registrations", e);
        }
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "field-registration-sweep",
                Schedule.fixedDelay(Duration.ofMinutes(2), Duration.ofHours(12)),
                this::sweep));
    }
}
