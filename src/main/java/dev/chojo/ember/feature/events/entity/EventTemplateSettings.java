/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.jspecify.annotations.Nullable;

/**
 * Everything an appointment template sets for the appointments made from it, as one edit writes it.
 *
 * <p>Fields, questions, reminders, audiences and the other lists a template carries are written on
 * their own and are not part of this.
 *
 * @param name                            the template's own name, shown where a template is picked
 * @param title                           the name the appointment takes, or null to leave it to whoever applies
 * @param description                     the description the appointment takes
 * @param categoryId                      the category the appointment takes
 * @param eventType                       how the appointment repeats
 * @param requiresRegistration            whether the appointment takes sign-ups
 * @param registrationDeadlineOffsetHours how many hours before the start sign-up closes, or null for no deadline
 * @param requiresConfirmation            whether sign-ups wait for a manager to confirm them
 * @param restrictionMode                 how the sign-up audience combines
 * @param attendanceTemplateId            the attendance sheet the appointment is taken on
 * @param registrationLimit               how many places the appointment has, or null for no limit
 * @param minRegistrations                how many accepted sign-ups each date needs, or null for no minimum
 * @param thresholdDays                   how many days before each date the minimum must be reached, or the
 *                                        date is called off
 * @param registrationCloseDays           how many days before each date sign-up closes, or null for no close
 */
public record EventTemplateSettings(
        String name,
        @Nullable String title,
        @Nullable String description,
        @Nullable Integer categoryId,
        StationEvent.@Nullable EventType eventType,
        @Nullable Boolean requiresRegistration,
        @Nullable Integer registrationDeadlineOffsetHours,
        @Nullable Boolean requiresConfirmation,
        @Nullable RestrictionMode restrictionMode,
        @Nullable Integer attendanceTemplateId,
        @Nullable Integer registrationLimit,
        @Nullable Integer minRegistrations,
        @Nullable Integer thresholdDays,
        @Nullable Integer registrationCloseDays) {

    /**
     * Whether any count or span is below zero, which no appointment could be made with.
     *
     * @return true where a number of hours, days or places is negative
     */
    public boolean hasNegativeRule() {
        return isNegative(registrationDeadlineOffsetHours)
                || isNegative(registrationLimit)
                || isNegative(minRegistrations)
                || isNegative(thresholdDays)
                || isNegative(registrationCloseDays);
    }

    private static boolean isNegative(@Nullable Integer value) {
        return value != null && value < 0;
    }
}
