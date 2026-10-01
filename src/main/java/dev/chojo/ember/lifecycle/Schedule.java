/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

import java.time.Duration;
import java.util.Objects;

/**
 * When a {@link ScheduledTask} runs.
 *
 * @param mode         how runs follow each other
 * @param initialDelay the wait between the start of the scheduler and the first run
 * @param period       the wait between two runs; {@link Duration#ZERO} for a task that runs once
 */
public record Schedule(ScheduleMode mode, Duration initialDelay, Duration period) {

    /** How the runs of a task follow each other. */
    public enum ScheduleMode {
        /** The next run starts one period after the previous one has finished. */
        FIXED_DELAY,
        /** Runs start one period apart; a run that is due while the previous one is still busy is skipped. */
        FIXED_RATE,
        /** One run after the initial delay and none after it. */
        ONCE
    }

    /**
     * Refuses a negative delay, and a period that is not positive in a repeating mode.
     */
    public Schedule {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(period, "period");
        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException("The initial delay must not be negative");
        }
        if (mode != ScheduleMode.ONCE && !period.isPositive()) {
            throw new IllegalArgumentException("A repeating schedule needs a positive period");
        }
    }

    /**
     * A {@link ScheduleMode#FIXED_DELAY} schedule.
     *
     * @param initialDelay the wait before the first run
     * @param period       the wait between the end of one run and the start of the next
     * @return the schedule
     */
    public static Schedule fixedDelay(Duration initialDelay, Duration period) {
        return new Schedule(ScheduleMode.FIXED_DELAY, initialDelay, period);
    }

    /**
     * A {@link ScheduleMode#FIXED_RATE} schedule.
     *
     * @param initialDelay the wait before the first run
     * @param period       the distance between the starts of two runs
     * @return the schedule
     */
    public static Schedule fixedRate(Duration initialDelay, Duration period) {
        return new Schedule(ScheduleMode.FIXED_RATE, initialDelay, period);
    }

    /**
     * A {@link ScheduleMode#ONCE} schedule.
     *
     * @param delay the wait before the run
     * @return the schedule
     */
    public static Schedule once(Duration delay) {
        return new Schedule(ScheduleMode.ONCE, delay, Duration.ZERO);
    }
}
