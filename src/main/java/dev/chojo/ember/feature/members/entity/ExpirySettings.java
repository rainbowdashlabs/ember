/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import java.util.List;
import java.util.Objects;

/**
 * What an expiry date field does, with every setting its configuration leaves out filled in.
 *
 * <p>The configuration keeps each of these optional, so a field written by a template, by an older
 * screen or by hand says only what it wants different. This is the one place that says what the
 * rest means; the browser holds the same defaults beside its copy of the state rule.
 *
 * @param warnFromDays     how many days before the date its value shows as running out
 * @param reminderDays     the days before the date on which a reminder goes out, without repeats
 * @param repeatEveryDays  how often a reminder goes out again after the date has passed, or
 *                         {@code null} where it goes out once
 * @param remindMember     whether the member, and whoever looks after them, is reminded
 * @param remindManagement whether the member management is reminded
 */
public record ExpirySettings(
        int warnFromDays,
        List<Integer> reminderDays,
        Integer repeatEveryDays,
        boolean remindMember,
        boolean remindManagement) {
    /** How close a date is when it starts to show, where the field does not say. */
    public static final int DEFAULT_WARN_FROM_DAYS = 30;

    /** When the reminders go out, where the field does not say. */
    public static final List<Integer> DEFAULT_REMINDER_DAYS = List.of(30, 7);

    /**
     * The settings of one field, the missing ones at their defaults: a month's warning, reminders a
     * month and a week ahead, to the member and not to the member management, and no repeats.
     *
     * @param config the field's configuration
     * @return the settings the field works by
     */
    public static ExpirySettings of(ProfileFieldConfig config) {
        List<Integer> reminderDays = config.reminderDays();
        return new ExpirySettings(
                Objects.requireNonNullElse(config.warnFromDays(), DEFAULT_WARN_FROM_DAYS),
                reminderDays == null
                        ? DEFAULT_REMINDER_DAYS
                        : reminderDays.stream()
                                .filter(Objects::nonNull)
                                .distinct()
                                .sorted()
                                .toList(),
                config.repeatEveryDays(),
                !Boolean.FALSE.equals(config.remindMember()),
                Boolean.TRUE.equals(config.remindManagement()));
    }

    /**
     * Whether the settings as written count days backwards or repeat without a gap.
     *
     * <p>Asked of the configuration rather than of the settings, because a default is never out of
     * range and only what somebody wrote down can be.
     *
     * @param config the configuration about to be saved
     * @return {@code true} where a warning or a reminder lies after the date or a repeat has no gap
     */
    public static boolean outOfRange(ProfileFieldConfig config) {
        Integer warnFromDays = config.warnFromDays();
        if (warnFromDays != null && warnFromDays < 0) return true;
        Integer repeatEveryDays = config.repeatEveryDays();
        if (repeatEveryDays != null && repeatEveryDays < 1) return true;
        List<Integer> reminderDays = config.reminderDays();
        return reminderDays != null && reminderDays.stream().anyMatch(days -> days == null || days < 0);
    }

    /**
     * Whether anybody is reminded at all. A field reminding nobody is left out of the sweep.
     */
    public boolean remindsAnybody() {
        return remindMember || remindManagement;
    }
}
