/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Where an expiry date stands on a given day.
 *
 * <p>The date is the last valid day: valid until the 31st means valid all of the 31st and expired
 * from the 1st. The browser holds the same rule to colour the member list without asking the server
 * per cell, and both are tested against the same examples.
 */
public enum ExpiryState {
    /** More days are left than the field warns from. */
    VALID,
    /** As many days are left as the field warns from, or fewer, and the last valid day has not passed. */
    EXPIRING,
    /** The last valid day has passed. */
    EXPIRED,
    /** No date was entered. */
    EMPTY;

    /**
     * The state of one date.
     *
     * @param lastValidDay the date as entered, or {@code null} where none was
     * @param today        the day it is asked on, on the station's clock
     * @param warnFromDays how many days before the date it starts to show as running out
     * @return where the date stands
     */
    public static ExpiryState of(LocalDate lastValidDay, LocalDate today, int warnFromDays) {
        if (lastValidDay == null) return EMPTY;
        long left = daysLeft(lastValidDay, today);
        if (left < 0) return EXPIRED;
        if (left <= warnFromDays) return EXPIRING;
        return VALID;
    }

    /**
     * How many days are left until the last valid day: 0 on the day itself, negative once it has
     * passed.
     */
    public static long daysLeft(LocalDate lastValidDay, LocalDate today) {
        return ChronoUnit.DAYS.between(today, lastValidDay);
    }
}
