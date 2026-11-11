/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.station.entity.ReminderTrigger;
import dev.chojo.ember.feature.station.entity.StationOverview;
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
 * Reminds the manager of a station that waits for them, a week and three weeks after it started waiting,
 * and never again after that.
 *
 * <p>The run looks in every hour, so a reminder is at most an hour late. Each station is handed to
 * {@link ManagerReminderService#remindAutomatically} on its own, which reads it afresh and writes the
 * reminder down before sending: a station that cannot be reminded now does not hold up the others, and
 * a run that overlaps another, or one of a second instance, never sends a reminder twice. Naming a new
 * manager starts the waiting period, and with it both reminders, anew.
 */
@Singleton
public class ManagerReminderTask implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(ManagerReminderTask.class);
    private static final Duration START_DELAY = Duration.ofMinutes(25);
    private static final Duration SWEEP_INTERVAL = Duration.ofHours(1);

    /** The later reminder first, so a station due for both after a long pause is only sent the later one. */
    private static final List<ReminderTrigger> TRIGGERS =
            List.of(ReminderTrigger.AFTER_21_DAYS, ReminderTrigger.AFTER_7_DAYS);

    private final ManagerReminderService reminders;

    @Inject
    public ManagerReminderTask(ManagerReminderService reminders) {
        this.reminders = reminders;
    }

    /**
     * Body of the run. A failure is logged and swallowed: what was due and not written stays due for the
     * next run.
     */
    void run() {
        try {
            sweep();
        } catch (RuntimeException e) {
            log.warn("Reminding station managers failed", e);
        }
    }

    /**
     * One run, reachable by tests so they need not wait. A failure for one station is logged and leaves the
     * others to go on.
     *
     * @return how many reminders went out
     */
    int sweep() {
        int sent = 0;
        for (ReminderTrigger trigger : TRIGGERS) {
            for (StationOverview station : reminders.dueFor(trigger)) {
                sent += remind(station, trigger) ? 1 : 0;
            }
        }
        if (sent > 0) log.info("Reminded {} station managers automatically", sent);
        return sent;
    }

    private boolean remind(StationOverview station, ReminderTrigger trigger) {
        var waitingSince = station.waitingSince();
        if (waitingSince == null) return false;
        try {
            return reminders.remindAutomatically(station.id(), trigger, waitingSince)
                    == ManagerReminderService.Outcome.SENT;
        } catch (RuntimeException e) {
            log.warn("Reminding the manager of station {} ({}) failed", station.id(), trigger, e);
            return false;
        }
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(
                new ScheduledTask("manager-reminders", Schedule.fixedDelay(START_DELAY, SWEEP_INTERVAL), this::run));
    }
}
