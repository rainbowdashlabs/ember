/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * A run of days: one date, or a date and every one after it.
 *
 * @param from  the first day
 * @param until the last day, or null where it runs on without end
 */
public record DateSpan(LocalDate from, @Nullable LocalDate until) {

    /** Just this one day. */
    public static DateSpan only(LocalDate date) {
        return new DateSpan(date, date);
    }

    /** This day and every one after it. */
    public static DateSpan onwardFrom(LocalDate date) {
        return new DateSpan(date, null);
    }
}
