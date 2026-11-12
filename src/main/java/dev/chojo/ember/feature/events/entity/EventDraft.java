/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * An appointment as the editor is about to save it: every setting of the appointment itself,
 * without what hangs off it (questions, files, audiences).
 *
 * @param name                  the name
 * @param description           the description, or null
 * @param eventType             how it repeats
 * @param dayOfWeek             the ISO weekday of a weekly series, or null
 * @param startTime             when it starts; for a series, the time of day and the first date
 * @param endTime               when it ends
 * @param templateId            the attendance sheet it is taken on, or null
 * @param requiresRegistration  whether members sign up for it
 * @param registrationDeadline  the deadline for signing up, or null
 * @param requiresConfirmation  whether a manager confirms each place
 * @param categoryId            the category, or null
 * @param isPublic              whether it is on the public calendar, or null to follow its category
 * @param registrationLimit     how many places there are, or null
 * @param minRegistrations      how many places a date needs, or null
 * @param thresholdDays         how many days ahead the minimum is checked, or null
 * @param registrationCloseDays how many days ahead of each date signing up closes, or null
 * @param repeatUntil           the last day a series may fall on, or null
 * @param repeatCount           how many times a series takes place, or null
 */
public record EventDraft(
        String name,
        @Nullable String description,
        StationEvent.EventType eventType,
        @Nullable Integer dayOfWeek,
        Instant startTime,
        Instant endTime,
        @Nullable Integer templateId,
        boolean requiresRegistration,
        @Nullable Instant registrationDeadline,
        boolean requiresConfirmation,
        @Nullable Integer categoryId,
        @Nullable Boolean isPublic,
        @Nullable Integer registrationLimit,
        @Nullable Integer minRegistrations,
        @Nullable Integer thresholdDays,
        @Nullable Integer registrationCloseDays,
        @Nullable LocalDate repeatUntil,
        @Nullable Integer repeatCount) {

    /** Whether the draft repeats on a schedule. */
    public boolean isRecurring() {
        return eventType != StationEvent.EventType.ONE_TIME;
    }
}
